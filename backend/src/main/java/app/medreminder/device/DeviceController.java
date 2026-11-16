package app.medreminder.device;

import app.medreminder.device.DeviceDtos.DeviceMeResponse;
import app.medreminder.device.DeviceDtos.DeviceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * /api/devices/me/** is for the paired phone (device token).
 * The rest is for family members (user token). SecurityConfig enforces which token goes where.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @GetMapping
    List<DeviceResponse> list(@RequestParam(required = false) UUID personId) {
        return service.list(personId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlink(@PathVariable UUID id) {
        service.unlink(id);
    }

    @GetMapping("/me")
    DeviceMeResponse me() {
        return service.me();
    }
}
