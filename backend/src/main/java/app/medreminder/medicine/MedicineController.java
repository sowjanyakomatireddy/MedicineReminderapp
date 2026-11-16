package app.medreminder.medicine;

import app.medreminder.medicine.MedicineDtos.MedicineRequest;
import app.medreminder.medicine.MedicineDtos.MedicineResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Medicines with their alarm times ("schedules"), which are created, changed and removed together
 * with the medicine. Viewers can read; only editors can change.
 */
@RestController
@RequestMapping("/api/medicines")
public class MedicineController {
    private final MedicineService service;

    public MedicineController(MedicineService service) {
        this.service = service;
    }

    @GetMapping
    List<MedicineResponse> list(@RequestParam(required = false) UUID personId) {
        return service.list(personId);
    }

    @GetMapping("/{id}")
    MedicineResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MedicineResponse create(@Valid @RequestBody MedicineRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    MedicineResponse update(@PathVariable UUID id, @Valid @RequestBody MedicineRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PutMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MedicineResponse setPhoto(@PathVariable UUID id, @RequestPart("photo") MultipartFile photo) {
        return service.setPhoto(id, photo);
    }

    @GetMapping("/{id}/photo")
    ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        return service.photo(id);
    }

    @DeleteMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePhoto(@PathVariable UUID id) {
        service.deletePhoto(id);
    }
}
