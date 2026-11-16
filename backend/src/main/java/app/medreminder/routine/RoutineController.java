package app.medreminder.routine;

import app.medreminder.routine.RoutineDtos.RoutineRequest;
import app.medreminder.routine.RoutineDtos.RoutineResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class RoutineController {
    private final RoutineService service;

    public RoutineController(RoutineService service) {
        this.service = service;
    }

    @GetMapping("/api/people/{personId}/routine")
    List<RoutineResponse> list(@PathVariable UUID personId) {
        return service.list(personId);
    }

    @PostMapping("/api/people/{personId}/routine")
    @ResponseStatus(HttpStatus.CREATED)
    RoutineResponse create(@PathVariable UUID personId, @Valid @RequestBody RoutineRequest req) {
        return service.create(personId, req);
    }

    @PutMapping("/api/routine/{id}")
    RoutineResponse update(@PathVariable UUID id, @Valid @RequestBody RoutineRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/api/routine/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
