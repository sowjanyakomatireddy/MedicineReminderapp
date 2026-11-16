package app.medreminder.period;

import app.medreminder.common.ApiException;
import app.medreminder.family.FamilyAccess;
import app.medreminder.family.FamilyMember;
import app.medreminder.person.Person;
import app.medreminder.person.PersonService;
import app.medreminder.sync.ScheduleChanged;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Period dates for one person: settings (editors), recorded start dates (any family member),
 * the next expected date, and the spoken reminder the phone rings N days before.
 */
@Service
public class PeriodService {
    public record Settings(boolean enabled, @NotNull @Min(15) @Max(60) Integer cycleLengthDays,
                           @NotNull @Min(1) @Max(10) Integer periodLengthDays,
                           @NotNull @Min(0) @Max(10) Integer remindDaysBefore, @NotNull LocalTime remindTime) {
    }

    public record StartRequest(LocalDate date) {
    }

    public record PeriodResponse(boolean enabled, int cycleLengthDays, int averageCycleDays, int periodLengthDays,
                                 int remindDaysBefore, @JsonFormat(pattern = "HH:mm") LocalTime remindTime,
                                 LocalDate today, LocalDate lastStart, LocalDate nextExpected, Integer daysUntil,
                                 LocalDate reminderDate, List<LocalDate> recentStarts) {
    }

    /** What the phone needs: ring a spoken reminder on this date at this time. */
    public record PhoneReminder(LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime time,
                                LocalDate expected) {
    }

    private final PeriodCycleRepository cycles;
    private final FamilyAccess access;
    private final PersonService people;
    private final ApplicationEventPublisher events;

    public PeriodService(PeriodCycleRepository cycles, FamilyAccess access, PersonService people,
                         ApplicationEventPublisher events) {
        this.cycles = cycles;
        this.access = access;
        this.people = people;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PeriodResponse get(UUID personId) {
        return describe(people.load(access.member().getFamilyId(), personId));
    }

    @Transactional
    public PeriodResponse saveSettings(UUID personId, Settings s) {
        Person p = people.load(access.editor().getFamilyId(), personId);
        p.setPeriodTracking(s.enabled());
        p.setPeriodSettings(s.cycleLengthDays(), s.periodLengthDays(), s.remindDaysBefore(), s.remindTime());
        events.publishEvent(new ScheduleChanged(p.getId()));
        return describe(p);
    }

    /** "Period started today" (or on a given date). Any family member may record it. */
    @Transactional
    public PeriodResponse recordStart(UUID personId, LocalDate date) {
        FamilyMember m = access.member();
        Person p = people.load(m.getFamilyId(), personId);
        LocalDate today = today(p);
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today) || day.isBefore(today.minusYears(2))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a date in the last two years, not in the future.");
        }
        if (cycles.findByPersonIdAndStartDate(p.getId(), day).isEmpty()) {
            cycles.save(new PeriodCycle(m.getFamilyId(), p.getId(), day));
        }
        events.publishEvent(new ScheduleChanged(p.getId()));
        return describe(p);
    }

    @Transactional
    public PeriodResponse deleteStart(UUID personId, LocalDate date) {
        Person p = people.load(access.member().getFamilyId(), personId);
        cycles.findByPersonIdAndStartDate(p.getId(), date).ifPresent(cycles::delete);
        events.publishEvent(new ScheduleChanged(p.getId()));
        return describe(p);
    }

    /** For the phone's sync: the next spoken reminder, if period reminders are on and one is coming up. */
    public PhoneReminder phoneReminder(Person p) {
        if (!p.isPeriodTracking()) {
            return null;
        }
        PeriodResponse r = describe(p);
        if (r.reminderDate() == null || r.reminderDate().isBefore(r.today())) {
            return null;
        }
        return new PhoneReminder(r.reminderDate(), r.remindTime(), r.nextExpected());
    }

    PeriodResponse describe(Person p) {
        List<LocalDate> starts = cycles.findByPersonIdOrderByStartDateAsc(p.getId()).stream()
                .map(PeriodCycle::getStartDate).toList();
        LocalDate today = today(p);
        int avg = PeriodMath.averageCycle(starts, p.getCycleLengthDays());
        LocalDate last = starts.isEmpty() ? null : starts.get(starts.size() - 1);
        LocalDate next = PeriodMath.nextExpected(last, avg, p.getPeriodLengthDays(), today);
        Integer daysUntil = next == null ? null : (int) ChronoUnit.DAYS.between(today, next);
        LocalDate reminder = next == null ? null : next.minusDays(p.getPeriodRemindDays());
        List<LocalDate> recent = starts.reversed().stream().limit(12).toList();
        return new PeriodResponse(p.isPeriodTracking(), p.getCycleLengthDays(), avg, p.getPeriodLengthDays(),
                p.getPeriodRemindDays(), p.getPeriodRemindTime(), today, last, next, daysUntil, reminder, recent);
    }

    private static LocalDate today(Person p) {
        return LocalDate.now(ZoneId.of(p.getTimeZone()));
    }
}
