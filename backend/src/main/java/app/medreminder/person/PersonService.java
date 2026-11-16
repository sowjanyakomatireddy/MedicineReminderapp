package app.medreminder.person;

import app.medreminder.common.ApiException;
import app.medreminder.device.DeviceRepository;
import app.medreminder.family.FamilyAccess;
import app.medreminder.family.FamilyMember;
import app.medreminder.medicine.Medicine;
import app.medreminder.medicine.MedicineRepository;
import app.medreminder.person.PersonDtos.PersonRequest;
import app.medreminder.person.PersonDtos.PersonResponse;
import app.medreminder.photo.PhotoService;
import app.medreminder.sync.ScheduleChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class PersonService {
    private final PersonRepository people;
    private final DeviceRepository devices;
    private final FamilyAccess access;
    private final MedicineRepository medicines;
    private final PhotoService photos;
    private final ApplicationEventPublisher events;

    public PersonService(PersonRepository people, DeviceRepository devices, FamilyAccess access,
                         MedicineRepository medicines, PhotoService photos, ApplicationEventPublisher events) {
        this.events = events;
        this.people = people;
        this.devices = devices;
        this.access = access;
        this.medicines = medicines;
        this.photos = photos;
    }

    @Transactional(readOnly = true)
    public List<PersonResponse> list() {
        FamilyMember m = access.member();
        return people.findByFamilyIdOrderByCreatedAt(m.getFamilyId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PersonResponse get(UUID id) {
        return toResponse(load(access.member().getFamilyId(), id));
    }

    @Transactional
    public PersonResponse create(PersonRequest req) {
        FamilyMember m = access.editor();
        Person p = new Person(m.getFamilyId(), req.name().trim(), req.voiceLanguage(), zone(req.timeZone()));
        p.setPhone(blankToNull(req.phone()));
        p.setFamilyPhone(blankToNull(req.familyPhone()));
        p.setPeriodTracking(Boolean.TRUE.equals(req.periodTracking()));
        return toResponse(people.save(p));
    }

    @Transactional
    public PersonResponse update(UUID id, PersonRequest req) {
        Person p = load(access.editor().getFamilyId(), id);
        p.setName(req.name().trim());
        p.setVoiceLanguage(req.voiceLanguage());
        p.setTimeZone(zone(req.timeZone()));
        p.setPhone(blankToNull(req.phone()));
        p.setFamilyPhone(blankToNull(req.familyPhone()));
        if (req.periodTracking() != null) {
            p.setPeriodTracking(req.periodTracking());
        }
        events.publishEvent(new ScheduleChanged(p.getId()));
        return toResponse(p);
    }

    /** Also removes their medicines and unlinks their phones (database cascade), and deletes their photos. */
    @Transactional
    public void delete(UUID id) {
        Person p = load(access.editor().getFamilyId(), id);
        List<String> photoKeys = new java.util.ArrayList<>();
        photoKeys.add(p.getPhotoKey());
        medicines.findByFamilyIdAndPersonIdOrderByCreatedAt(p.getFamilyId(), p.getId()).stream()
                .map(Medicine::getPhotoKey).forEach(photoKeys::add);
        people.delete(p);
        photoKeys.forEach(photos::delete);
    }

    @Transactional
    public PersonResponse setPhoto(UUID id, MultipartFile file) {
        Person p = load(access.editor().getFamilyId(), id);
        p.setPhotoKey(photos.replace(p.getPhotoKey(), file));
        return toResponse(p);
    }

    @Transactional
    public void deletePhoto(UUID id) {
        Person p = load(access.editor().getFamilyId(), id);
        photos.delete(p.getPhotoKey());
        p.setPhotoKey(null);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> photo(UUID id) {
        return photos.serve(load(access.member().getFamilyId(), id).getPhotoKey());
    }

    /** Loads a person only if they belong to the given family; otherwise 404 (we never reveal other families). */
    public Person load(UUID familyId, UUID personId) {
        return people.findByIdAndFamilyId(personId, familyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found."));
    }

    public PersonResponse toResponse(Person p) {
        return new PersonResponse(p.getId(), p.getName(), p.getVoiceLanguage(), p.getTimeZone(),
                devices.countByPersonIdAndRevokedAtIsNull(p.getId()), medicines.countByPersonId(p.getId()),
                p.getPhotoKey() != null, PhotoService.version(p.getPhotoKey()), p.getPhone(), p.getFamilyPhone(),
                p.isPeriodTracking());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String zone(String tz) {
        if (tz == null || tz.isBlank()) {
            return Person.DEFAULT_TIME_ZONE;
        }
        try {
            return ZoneId.of(tz.trim()).getId();
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown time zone.");
        }
    }
}
