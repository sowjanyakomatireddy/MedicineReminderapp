package app.medreminder.device;

import app.medreminder.person.PersonDtos.PersonResponse;

import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record DeviceResponse(UUID id, UUID personId, String name, String platform, Instant createdAt,
                                 Instant lastSyncAt) {
    }

    /** What a paired phone learns about itself. */
    public record DeviceMeResponse(UUID deviceId, String familyName, PersonResponse person) {
    }
}
