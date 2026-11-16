package app.medreminder.person;

import app.medreminder.person.PersonDtos.PersonRequest;
import app.medreminder.person.PersonDtos.PersonResponse;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** People we remind, in the caller's family. Viewers can read; only editors can change. */
@RestController
@RequestMapping("/api/people")
public class PersonController {
    private final PersonService service;

    public PersonController(PersonService service) {
        this.service = service;
    }

    @GetMapping
    List<PersonResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    PersonResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PersonResponse create(@Valid @RequestBody PersonRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    PersonResponse update(@PathVariable UUID id, @Valid @RequestBody PersonRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PutMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PersonResponse setPhoto(@PathVariable UUID id, @RequestPart("photo") MultipartFile photo) {
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
