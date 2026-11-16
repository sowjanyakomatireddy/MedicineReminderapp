package app.medreminder.sync;

import app.medreminder.common.ApiException;
import app.medreminder.device.Device;
import app.medreminder.device.DeviceRepository;
import app.medreminder.dose.DoseDtos.Accepted;
import app.medreminder.dose.DoseDtos.PhoneLog;
import app.medreminder.dose.DoseDtos.TokenRequest;
import app.medreminder.dose.DoseLog;
import app.medreminder.dose.DoseLogService;
import app.medreminder.family.FamilyService;
import app.medreminder.medicine.Medicine;
import app.medreminder.medicine.MedicineDtos.MedicineResponse;
import app.medreminder.medicine.MedicineRepository;
import app.medreminder.medicine.MedicineService;
import app.medreminder.person.Person;
import app.medreminder.person.PersonRepository;
import app.medreminder.photo.PhotoService;
import app.medreminder.push.Notifier;
import app.medreminder.security.CurrentDevice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * What the elder's phone calls with its device token. The phone fetches /sync when it gets a SYNC push,
 * when the app starts, and once a day; it uploads its offline TAKEN/MISSED queue to /dose-logs.
 */
@RestController
@RequestMapping("/api/devices/me")
public class DeviceSyncController {
    public record PersonInfo(UUID id, String name, String voiceLanguage, String timeZone, String familyPhone) {
    }

    public record LogInfo(UUID scheduleId, LocalDate date, DoseLog.Status status) {
    }

    /** Everything the phone needs to ring alarms with no internet. Medicines include paused times (active=false). */
    public record SyncResponse(PersonInfo person, String familyName, List<MedicineResponse> medicines,
                               List<LogInfo> logs, Instant serverTime, boolean pushEnabled) {
    }

    private final DeviceRepository devices;
    private final PersonRepository people;
    private final MedicineRepository medicines;
    private final MedicineService medicineService;
    private final FamilyService families;
    private final DoseLogService doses;
    private final PhotoService photos;
    private final Notifier notifier;

    public DeviceSyncController(DeviceRepository devices, PersonRepository people, MedicineRepository medicines,
                                MedicineService medicineService, FamilyService families, DoseLogService doses,
                                PhotoService photos, Notifier notifier) {
        this.devices = devices;
        this.people = people;
        this.medicines = medicines;
        this.medicineService = medicineService;
        this.families = families;
        this.doses = doses;
        this.photos = photos;
        this.notifier = notifier;
    }

    @GetMapping("/sync")
    @Transactional
    public SyncResponse sync() {
        Device d = device();
        Person p = people.findById(d.getPersonId()).orElseThrow();
        LocalDate today = LocalDate.now(ZoneId.of(p.getTimeZone()));
        List<LogInfo> logs = doses.forPerson(p.getId(), today.minusDays(1), today).stream()
                .filter(l -> l.getScheduleId() != null)
                .map(l -> new LogInfo(l.getScheduleId(), l.getDoseDate(), l.getStatus())).toList();
        List<MedicineResponse> meds = medicineService.forPersonInternal(p.getFamilyId(), p.getId());
        d.markSynced();
        return new SyncResponse(new PersonInfo(p.getId(), p.getName(), p.getVoiceLanguage(), p.getTimeZone(),
                p.getFamilyPhone()), families.load(p.getFamilyId()).getName(), meds, logs, Instant.now(),
                notifier.enabled());
    }

    /** The phone's Firebase token, so the server can wake it (SYNC, RING). */
    @PutMapping("/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void pushToken(@Valid @RequestBody TokenRequest req) {
        device().setFcmToken(req.token());
    }

    /** The phone's offline queue, in batches. Idempotent: the same log twice is stored once. */
    @PostMapping("/dose-logs")
    public Accepted doseLogs(@RequestBody @Size(max = 200) List<@Valid PhoneLog> batch) {
        return new Accepted(doses.recordFromPhone(device().getPersonId(), batch));
    }

    /** Medicine photos for the alarm screen; the phone keeps a copy so it works offline. */
    @GetMapping("/medicines/{id}/photo")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        Device d = device();
        Medicine m = medicines.findById(id)
                .filter(x -> x.getPersonId().equals(d.getPersonId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No photo."));
        return photos.serve(m.getPhotoKey());
    }

    private Device device() {
        return devices.findByIdAndRevokedAtIsNull(CurrentDevice.get().deviceId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "This phone is no longer linked."));
    }
}
