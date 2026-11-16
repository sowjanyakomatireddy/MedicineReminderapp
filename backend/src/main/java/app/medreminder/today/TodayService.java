package app.medreminder.today;

import app.medreminder.dose.DoseLog;
import app.medreminder.dose.DoseLogService;
import app.medreminder.family.FamilyAccess;
import com.fasterxml.jackson.annotation.JsonFormat;
import app.medreminder.medicine.FoodRule;
import app.medreminder.medicine.Medicine;
import app.medreminder.medicine.MedicineRepository;
import app.medreminder.medicine.PillColor;
import app.medreminder.medicine.PillShape;
import app.medreminder.medicine.Schedule;
import app.medreminder.medicine.ScheduleRepository;
import app.medreminder.person.Person;
import app.medreminder.person.PersonRepository;
import app.medreminder.photo.PhotoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Today's doses for each person, in that person's own time zone.
 * Until the phone reports doses (Phase 5), a dose whose alarm window has ended is UNCONFIRMED;
 * Phase 5 turns that into TAKEN or MISSED from the dose log.
 */
@Service
public class TodayService {
    public enum Status {
        /** Before the first reminder. */
        UPCOMING,
        /** The phone is ringing now (between the first reminder and the automatic stop). */
        DUE,
        /** The alarm window has ended and no answer has arrived yet (the server marks it missed shortly). */
        UNCONFIRMED,
        TAKEN,
        MISSED
    }

    public record Dose(UUID scheduleId, UUID medicineId, String name, String voiceName, PillShape shape,
                       PillColor color, boolean hasPhoto, String photoVersion, @JsonFormat(pattern = "HH:mm") LocalTime time,
                       @JsonFormat(pattern = "HH:mm") LocalTime alertAt,
                       FoodRule food, Status status, UUID doseLogId) {
    }

    public record PersonToday(UUID personId, String name, String voiceLanguage, LocalDate date, int total, int taken,
                              List<Dose> doses) {
    }

    private final FamilyAccess access;
    private final PersonRepository people;
    private final MedicineRepository medicines;
    private final ScheduleRepository schedules;
    private final DoseLogService doses;

    public TodayService(FamilyAccess access, PersonRepository people, MedicineRepository medicines,
                        ScheduleRepository schedules, DoseLogService doses) {
        this.doses = doses;
        this.access = access;
        this.people = people;
        this.medicines = medicines;
        this.schedules = schedules;
    }

    @Transactional(readOnly = true)
    public List<PersonToday> today(Instant now) {
        UUID familyId = access.member().getFamilyId();
        Map<UUID, Medicine> meds = medicines.findByFamilyIdOrderByCreatedAt(familyId).stream()
                .collect(Collectors.toMap(Medicine::getId, Function.identity()));
        Map<UUID, List<Schedule>> byPerson = schedules.findByMedicineIdInOrderByTimeAscCreatedAtAsc(meds.keySet()).stream()
                .collect(Collectors.groupingBy(s -> meds.get(s.getMedicineId()).getPersonId()));
        List<Person> family = people.findByFamilyIdOrderByCreatedAt(familyId);
        LocalDate wide = LocalDate.ofInstant(now, ZoneId.of("UTC"));
        Map<String, DoseLog> answers = doses.forPeople(family.stream().map(Person::getId).toList(),
                        wide.minusDays(1), wide.plusDays(1)).stream()
                .filter(l -> l.getScheduleId() != null)
                .collect(Collectors.toMap(l -> l.getScheduleId() + "@" + l.getDoseDate(), l -> l, (a, b) -> a));
        return family.stream()
                .map(p -> forPerson(p, byPerson.getOrDefault(p.getId(), List.of()), meds, answers, now))
                .toList();
    }

    static PersonToday forPerson(Person p, List<Schedule> list, Map<UUID, Medicine> meds, Instant now) {
        return forPerson(p, list, meds, Map.of(), now);
    }

    static PersonToday forPerson(Person p, List<Schedule> list, Map<UUID, Medicine> meds,
                                 Map<String, DoseLog> answers, Instant now) {
        ZoneId zone = ZoneId.of(p.getTimeZone());
        LocalDate date = now.atZone(zone).toLocalDate();
        List<Dose> doses = list.stream()
                .filter(s -> s.isActive() && s.runsOn(date.getDayOfWeek()))
                .map(s -> {
                    Medicine m = meds.get(s.getMedicineId());
                    DoseLog answer = answers.get(s.getId() + "@" + date);
                    Status status = answer == null ? status(s, date, zone, now) : Status.valueOf(answer.getStatus().name());
                    return new Dose(s.getId(), m.getId(), m.getName(), m.getVoiceName(), m.getShape(), m.getColor(),
                            m.getPhotoKey() != null, PhotoService.version(m.getPhotoKey()), s.getTime(),
                            s.getTime().minusMinutes(s.getRemindBeforeMin()), s.getFood(), status,
                            answer == null ? null : answer.getId());
                })
                .sorted(Comparator.comparing(Dose::time))
                .toList();
        int taken = (int) doses.stream().filter(d -> d.status() == Status.TAKEN).count();
        return new PersonToday(p.getId(), p.getName(), p.getVoiceLanguage(), date, doses.size(), taken, doses);
    }

    static Status status(Schedule s, LocalDate date, ZoneId zone, Instant now) {
        ZonedDateTime at = date.atTime(s.getTime()).atZone(zone);
        Instant firstRing = at.minusMinutes(s.getRemindBeforeMin()).toInstant();
        Instant stop = at.plusMinutes(s.getStopAfterMin()).toInstant();
        if (now.isBefore(firstRing)) {
            return Status.UPCOMING;
        }
        return now.isBefore(stop) ? Status.DUE : Status.UNCONFIRMED;
    }
}
