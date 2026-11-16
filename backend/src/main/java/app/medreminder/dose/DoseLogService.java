package app.medreminder.dose;

import app.medreminder.common.ApiException;
import app.medreminder.config.AppProperties;
import app.medreminder.device.Device;
import app.medreminder.device.DeviceRepository;
import app.medreminder.dose.DoseDtos.DaySummary;
import app.medreminder.dose.DoseDtos.DoseDetail;
import app.medreminder.dose.DoseDtos.PhoneLog;
import app.medreminder.family.FamilyAccess;
import app.medreminder.medicine.Medicine;
import app.medreminder.medicine.MedicineRepository;
import app.medreminder.medicine.Schedule;
import app.medreminder.medicine.ScheduleRepository;
import app.medreminder.person.Person;
import app.medreminder.person.PersonRepository;
import app.medreminder.photo.PhotoService;
import app.medreminder.push.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TAKEN / MISSED for each dose. Rules:
 * - one answer per dose (schedule + date); a later TAKEN replaces MISSED, never the other way round;
 * - the phone's logs are idempotent by their id;
 * - if no answer arrives by the automatic stop time + a short grace, the server marks it MISSED;
 * - every new MISSED sends an alert to the whole family.
 */
@Service
public class DoseLogService {
    private static final Logger log = LoggerFactory.getLogger(DoseLogService.class);
    /** Don't mark doses missed that ended long ago (e.g. after the server was down for a day). */
    private static final Duration LOOK_BACK = Duration.ofHours(12);

    private final DoseLogRepository logs;
    private final ScheduleRepository schedules;
    private final MedicineRepository medicines;
    private final PersonRepository people;
    private final DeviceRepository devices;
    private final FamilyAccess access;
    private final Notifier notifier;
    private final Duration grace;

    public DoseLogService(DoseLogRepository logs, ScheduleRepository schedules, MedicineRepository medicines,
                          PersonRepository people, DeviceRepository devices, FamilyAccess access, Notifier notifier,
                          AppProperties props) {
        this.logs = logs;
        this.schedules = schedules;
        this.medicines = medicines;
        this.people = people;
        this.devices = devices;
        this.access = access;
        this.notifier = notifier;
        this.grace = Duration.ofMinutes(props.jobs().missedGraceMinutes());
    }

    // ------------------------------------------------------------------ from the elder's phone

    /** Stores the phone's queued logs. Returns the ids the phone may forget (all of them, unless saving failed). */
    @Transactional
    public List<String> recordFromPhone(UUID personId, List<PhoneLog> batch) {
        List<String> accepted = new ArrayList<>();
        for (PhoneLog l : batch) {
            accepted.add(l.id());
            Optional<UUID> id = uuid(l.id());
            Optional<UUID> scheduleId = uuid(l.scheduleId());
            if (id.isEmpty() || scheduleId.isEmpty() || logs.existsById(id.get())) {
                continue; // a test or sample alarm, or already stored
            }
            Schedule s = schedules.findById(scheduleId.get()).orElse(null);
            Medicine m = s == null ? null : medicines.findById(s.getMedicineId()).orElse(null);
            if (m == null || !m.getPersonId().equals(personId)) {
                continue; // deleted meanwhile, or not this person's: nothing to record
            }
            Instant at = l.at() == null ? Instant.now() : Instant.ofEpochMilli(l.at());
            Optional<DoseLog> existing = logs.findByScheduleIdAndDoseDate(s.getId(), l.date());
            if (existing.isPresent()) {
                if (l.status() == DoseLog.Status.TAKEN && existing.get().getStatus() == DoseLog.Status.MISSED) {
                    existing.get().markTaken(DoseLog.Source.PHONE, at);
                }
                continue;
            }
            DoseLog saved = logs.save(new DoseLog(id.get(), m.getFamilyId(), personId, s.getId(), m.getId(),
                    m.spokenName(), l.date(), s.getTime(), l.status(), DoseLog.Source.PHONE, at));
            if (saved.getStatus() == DoseLog.Status.MISSED) {
                people.findById(personId).ifPresent(p -> notifier.missed(saved, p));
            }
        }
        return accepted;
    }

    // ------------------------------------------------------------------ the missed-dose job

    /**
     * Marks every dose whose alarm window ended (plus grace) without an answer as MISSED, and alerts the family.
     * Each insert is its own transaction, so a clash with a log arriving from the phone at the same moment is
     * simply skipped. Returns how many were marked.
     */
    public int markMissed(Instant now) {
        List<Schedule> active = schedules.findByActiveTrue();
        Map<UUID, Medicine> meds = medicines.findAllById(active.stream().map(Schedule::getMedicineId).toList())
                .stream().collect(Collectors.toMap(Medicine::getId, Function.identity()));
        Map<UUID, Person> persons = people.findAllById(meds.values().stream().map(Medicine::getPersonId).toList())
                .stream().collect(Collectors.toMap(Person::getId, Function.identity()));
        int marked = 0;
        for (Schedule s : active) {
            Medicine m = meds.get(s.getMedicineId());
            Person p = m == null ? null : persons.get(m.getPersonId());
            if (p == null) {
                continue;
            }
            ZoneId zone = ZoneId.of(p.getTimeZone());
            LocalDate today = now.atZone(zone).toLocalDate();
            for (LocalDate date : List.of(today.minusDays(1), today)) {
                if (!s.runsOn(date.getDayOfWeek())) {
                    continue;
                }
                ZonedDateTime doseAt = date.atTime(s.getTime()).atZone(zone);
                Instant start = doseAt.minusMinutes(s.getRemindBeforeMin()).toInstant();
                Instant due = doseAt.plusMinutes(s.getStopAfterMin()).toInstant().plus(grace);
                if (due.isAfter(now) || due.isBefore(now.minus(LOOK_BACK)) || s.getCreatedAt().isAfter(start)
                        || logs.existsByScheduleIdAndDoseDate(s.getId(), date)) {
                    continue;
                }
                try {
                    DoseLog missed = logs.saveAndFlush(new DoseLog(UUID.randomUUID(), m.getFamilyId(), p.getId(),
                            s.getId(), m.getId(), m.spokenName(), date, s.getTime(), DoseLog.Status.MISSED,
                            DoseLog.Source.SERVER, now));
                    notifier.missed(missed, p);
                    marked++;
                } catch (DataIntegrityViolationException race) {
                    log.debug("Dose answered at the same moment; skipped");
                }
            }
        }
        return marked;
    }

    // ------------------------------------------------------------------ family app

    /** Missed doses from today and yesterday, newest first (Family Home banner). */
    @Transactional(readOnly = true)
    public List<DoseDetail> alerts() {
        UUID familyId = access.member().getFamilyId();
        return logs.findByFamilyIdAndStatusAndDoseDateGreaterThanEqualOrderByDoseDateDescDoseTimeDesc(
                        familyId, DoseLog.Status.MISSED, LocalDate.now().minusDays(1))
                .stream().map(this::detail).toList();
    }

    @Transactional(readOnly = true)
    public DoseDetail get(UUID id) {
        return detail(load(id));
    }

    /** "He already took it. Mark as taken". Any family member may do this. */
    @Transactional
    public DoseDetail markTaken(UUID id) {
        DoseLog l = load(id);
        if (l.getStatus() == DoseLog.Status.MISSED) {
            l.markTaken(DoseLog.Source.FAMILY, Instant.now());
        }
        return detail(l);
    }

    /** "Play the reminder again": a push that wakes the person's phone and rings this medicine now. */
    @Transactional(readOnly = true)
    public void replay(UUID id) {
        DoseLog l = load(id);
        if (!notifier.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Push messages are not set up on the server.");
        }
        if (l.getScheduleId() == null || notifier.ring(l.getPersonId(), l.getScheduleId(), l.getDoseDate()) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Their phone cannot be reached. Try calling.");
        }
    }

    /** The last 7 days for one person: how many doses were due, taken and missed each day. */
    @Transactional(readOnly = true)
    public List<DaySummary> week(UUID personId) {
        UUID familyId = access.member().getFamilyId();
        Person p = people.findByIdAndFamilyId(personId, familyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found."));
        ZoneId zone = ZoneId.of(p.getTimeZone());
        LocalDate today = LocalDate.now(zone);
        List<Medicine> meds = medicines.findByFamilyIdAndPersonIdOrderByCreatedAt(familyId, personId);
        List<Schedule> times = schedules.findByMedicineIdInOrderByTimeAscCreatedAtAsc(
                meds.stream().map(Medicine::getId).toList());
        List<DoseLog> week = logs.findByPersonIdAndDoseDateBetween(personId, today.minusDays(6), today);
        List<DaySummary> out = new ArrayList<>();
        for (LocalDate d = today.minusDays(6); !d.isAfter(today); d = d.plusDays(1)) {
            LocalDate day = d;
            Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
            int total = (int) times.stream()
                    .filter(s -> s.isActive() && s.runsOn(day.getDayOfWeek()) && s.getCreatedAt().isBefore(dayEnd))
                    .count();
            int taken = (int) week.stream().filter(l -> l.getDoseDate().equals(day)
                    && l.getStatus() == DoseLog.Status.TAKEN).count();
            int missed = (int) week.stream().filter(l -> l.getDoseDate().equals(day)
                    && l.getStatus() == DoseLog.Status.MISSED).count();
            out.add(new DaySummary(day, Math.max(total, taken + missed), taken, missed));
        }
        return out;
    }

    private DoseLog load(UUID id) {
        return logs.findByIdAndFamilyId(id, access.member().getFamilyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dose not found."));
    }

    private DoseDetail detail(DoseLog l) {
        Person p = people.findById(l.getPersonId()).orElseThrow();
        Medicine m = l.getMedicineId() == null ? null : medicines.findById(l.getMedicineId()).orElse(null);
        Schedule s = l.getScheduleId() == null ? null : schedules.findById(l.getScheduleId()).orElse(null);
        boolean phoneLinked = devices.countByPersonIdAndRevokedAtIsNull(p.getId()) > 0;
        return new DoseDetail(l.getId(), p.getId(), p.getName(), p.getPhone(),
                l.getMedicineId(), m != null ? m.getName() : l.getMedicineName(), m != null ? m.getVoiceName() : null,
                m != null ? m.getShape() : null, m != null ? m.getColor() : null,
                m != null && m.getPhotoKey() != null, m != null ? PhotoService.version(m.getPhotoKey()) : null,
                s != null ? s.getFood() : null, l.getDoseDate(), l.getDoseTime(), l.getStatus(), l.getSource(),
                l.getLoggedAt(), s != null ? rings(s) : null, phoneLinked, notifier.enabled());
    }

    /** How many times the alarm rings for one dose: every repeatEvery minutes, from first reminder to auto stop. */
    static int rings(Schedule s) {
        int window = s.getRemindBeforeMin() + s.getStopAfterMin();
        return (window + s.getRepeatEveryMin() - 1) / s.getRepeatEveryMin();
    }

    private static Optional<UUID> uuid(String s) {
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Used by the device sync: logs for the given dates (today and yesterday on the phone). */
    @Transactional(readOnly = true)
    public List<DoseLog> forPerson(UUID personId, LocalDate from, LocalDate to) {
        return logs.findByPersonIdAndDoseDateBetween(personId, from, to);
    }

    /** Used by Family Home's today view. */
    @Transactional(readOnly = true)
    public List<DoseLog> forPeople(List<UUID> personIds, LocalDate from, LocalDate to) {
        return logs.findByPersonIdInAndDoseDateBetween(personIds, from, to);
    }

    Device device(UUID id) {
        return devices.findByIdAndRevokedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "This phone is no longer linked."));
    }
}
