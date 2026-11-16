package app.medreminder.device;

import app.medreminder.common.ApiException;
import app.medreminder.device.DeviceDtos.DeviceMeResponse;
import app.medreminder.device.DeviceDtos.DeviceResponse;
import app.medreminder.family.FamilyAccess;
import app.medreminder.family.FamilyMember;
import app.medreminder.family.FamilyService;
import app.medreminder.person.PersonService;
import app.medreminder.security.CurrentDevice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {
    private final DeviceRepository devices;
    private final FamilyAccess access;
    private final FamilyService families;
    private final PersonService people;

    public DeviceService(DeviceRepository devices, FamilyAccess access, FamilyService families, PersonService people) {
        this.devices = devices;
        this.access = access;
        this.families = families;
        this.people = people;
    }

    /** Phones linked in the caller's family, optionally only one person's. */
    @Transactional(readOnly = true)
    public List<DeviceResponse> list(UUID personId) {
        UUID familyId = access.member().getFamilyId();
        List<Device> list = personId == null
                ? devices.findByFamilyIdAndRevokedAtIsNullOrderByCreatedAt(familyId)
                : devices.findByFamilyIdAndPersonIdAndRevokedAtIsNullOrderByCreatedAt(familyId, personId);
        return list.stream().map(DeviceService::toResponse).toList();
    }

    @Transactional
    public void unlink(UUID deviceId) {
        FamilyMember m = access.editor();
        Device d = devices.findByIdAndFamilyIdAndRevokedAtIsNull(deviceId, m.getFamilyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Phone not found."));
        d.revoke();
    }

    /** Called with a device token. */
    @Transactional(readOnly = true)
    public DeviceMeResponse me() {
        Device d = devices.findByIdAndRevokedAtIsNull(CurrentDevice.get().deviceId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "This phone is no longer linked."));
        return new DeviceMeResponse(d.getId(), families.load(d.getFamilyId()).getName(),
                people.toResponse(people.load(d.getFamilyId(), d.getPersonId())));
    }

    static DeviceResponse toResponse(Device d) {
        return new DeviceResponse(d.getId(), d.getPersonId(), d.getName(), d.getPlatform(), d.getCreatedAt(),
                d.getLastSyncAt());
    }
}
