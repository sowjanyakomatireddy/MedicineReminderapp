package app.medreminder.medicine;

import app.medreminder.common.ApiException;
import app.medreminder.family.FamilyAccess;
import app.medreminder.family.FamilyMember;
import app.medreminder.medicine.MedicineDtos.MedicineRequest;
import app.medreminder.medicine.MedicineDtos.MedicineResponse;
import app.medreminder.medicine.MedicineDtos.ScheduleRequest;
import app.medreminder.medicine.MedicineDtos.ScheduleResponse;
import app.medreminder.person.PersonService;
import app.medreminder.photo.PhotoService;
import app.medreminder.sync.ScheduleChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Medicines and their alarm times. Everything is scoped to the caller's family; only editors change things. */
@Service
public class MedicineService {
    private final MedicineRepository medicines;
    private final ScheduleRepository schedules;
    private final FamilyAccess access;
    private final PersonService people;
    private final PhotoService photos;
    private final ApplicationEventPublisher events;

    public MedicineService(MedicineRepository medicines, ScheduleRepository schedules, FamilyAccess access,
                           PersonService people, PhotoService photos, ApplicationEventPublisher events) {
        this.events = events;
        this.medicines = medicines;
        this.schedules = schedules;
        this.access = access;
        this.people = people;
        this.photos = photos;
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> list(UUID personId) {
        UUID familyId = access.member().getFamilyId();
        List<Medicine> list = personId == null
                ? medicines.findByFamilyIdOrderByCreatedAt(familyId)
                : medicines.findByFamilyIdAndPersonIdOrderByCreatedAt(familyId, personId);
        return toResponses(list);
    }

    @Transactional(readOnly = true)
    public MedicineResponse get(UUID id) {
        return toResponses(List.of(load(access.member().getFamilyId(), id))).get(0);
    }

    @Transactional
    public MedicineResponse create(MedicineRequest req) {
        FamilyMember m = access.editor();
        if (req.personId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose who this medicine is for.");
        }
        people.load(m.getFamilyId(), req.personId()); // 404 if not in this family
        Medicine med = new Medicine(m.getFamilyId(), req.personId());
        apply(med, req);
        medicines.save(med);
        syncSchedules(med.getId(), req.schedules());
        events.publishEvent(new ScheduleChanged(med.getPersonId()));
        return get(med.getId());
    }

    @Transactional
    public MedicineResponse update(UUID id, MedicineRequest req) {
        Medicine med = load(access.editor().getFamilyId(), id);
        apply(med, req);
        syncSchedules(med.getId(), req.schedules());
        events.publishEvent(new ScheduleChanged(med.getPersonId()));
        return get(med.getId());
    }

    @Transactional
    public void delete(UUID id) {
        Medicine med = load(access.editor().getFamilyId(), id);
        String photo = med.getPhotoKey();
        medicines.delete(med); // schedules go with it (database cascade)
        photos.delete(photo);
        events.publishEvent(new ScheduleChanged(med.getPersonId()));
    }

    @Transactional
    public MedicineResponse setPhoto(UUID id, MultipartFile file) {
        Medicine med = load(access.editor().getFamilyId(), id);
        med.setPhotoKey(photos.replace(med.getPhotoKey(), file));
        events.publishEvent(new ScheduleChanged(med.getPersonId()));
        return get(id);
    }

    @Transactional
    public void deletePhoto(UUID id) {
        Medicine med = load(access.editor().getFamilyId(), id);
        photos.delete(med.getPhotoKey());
        med.setPhotoKey(null);
        events.publishEvent(new ScheduleChanged(med.getPersonId()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> photo(UUID id) {
        return photos.serve(load(access.member().getFamilyId(), id).getPhotoKey());
    }

    /** For the device sync (no family login there): one person's medicines with their times. */
    @Transactional(readOnly = true)
    public List<MedicineResponse> forPersonInternal(UUID familyId, UUID personId) {
        return toResponses(medicines.findByFamilyIdAndPersonIdOrderByCreatedAt(familyId, personId));
    }

    public Medicine load(UUID familyId, UUID id) {
        return medicines.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Medicine not found."));
    }

    private static void apply(Medicine med, MedicineRequest req) {
        med.update(req.name().trim(), blankToNull(req.voiceName()), req.shape(), req.color(), blankToNull(req.notes()));
    }

    /** Updates times that have an id, adds new ones, deletes ones no longer sent. Keeps ids stable for dose logs. */
    private void syncSchedules(UUID medicineId, List<ScheduleRequest> wanted) {
        Map<UUID, Schedule> existing = schedules.findByMedicineIdOrderByTimeAscCreatedAtAsc(medicineId).stream()
                .collect(Collectors.toMap(Schedule::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        for (ScheduleRequest r : wanted) {
            Schedule s;
            if (r.id() == null) {
                s = new Schedule(medicineId);
            } else {
                s = existing.get(r.id());
                if (s == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "One of the times does not belong to this medicine.");
                }
                kept.add(s.getId());
            }
            s.update(r.time(), r.days(), r.food(),
                    orDefault(r.remindBeforeMin(), Schedule.DEFAULT_REMIND_BEFORE),
                    orDefault(r.repeatEveryMin(), Schedule.DEFAULT_REPEAT_EVERY),
                    orDefault(r.stopAfterMin(), Schedule.DEFAULT_STOP_AFTER),
                    r.active() == null || r.active());
            schedules.save(s);
        }
        existing.values().stream().filter(s -> !kept.contains(s.getId())).forEach(schedules::delete);
        schedules.flush();
    }

    List<MedicineResponse> toResponses(List<Medicine> list) {
        Map<UUID, List<Schedule>> byMedicine = schedules
                .findByMedicineIdInOrderByTimeAscCreatedAtAsc(list.stream().map(Medicine::getId).toList()).stream()
                .collect(Collectors.groupingBy(Schedule::getMedicineId));
        return list.stream().map(m -> new MedicineResponse(m.getId(), m.getPersonId(), m.getName(), m.getVoiceName(),
                m.getShape(), m.getColor(), m.getNotes(), m.getPhotoKey() != null, PhotoService.version(m.getPhotoKey()),
                byMedicine.getOrDefault(m.getId(), List.of()).stream().map(ScheduleResponse::of).toList(),
                m.getUpdatedAt())).toList();
    }

    private static int orDefault(Integer v, int d) {
        return v == null ? d : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
