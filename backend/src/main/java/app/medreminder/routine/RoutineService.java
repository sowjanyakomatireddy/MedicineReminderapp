package app.medreminder.routine;

import app.medreminder.common.ApiException;
import app.medreminder.family.FamilyAccess;
import app.medreminder.family.FamilyMember;
import app.medreminder.person.Person;
import app.medreminder.person.PersonService;
import app.medreminder.routine.RoutineDtos.RoutineRequest;
import app.medreminder.routine.RoutineDtos.RoutineResponse;
import app.medreminder.sync.ScheduleChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Routine items per person. Viewers can read; editors change. Each change syncs the person's phone. */
@Service
public class RoutineService {
    private final RoutineRepository routine;
    private final FamilyAccess access;
    private final PersonService people;
    private final ApplicationEventPublisher events;

    public RoutineService(RoutineRepository routine, FamilyAccess access, PersonService people,
                          ApplicationEventPublisher events) {
        this.routine = routine;
        this.access = access;
        this.people = people;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<RoutineResponse> list(UUID personId) {
        Person p = people.load(access.member().getFamilyId(), personId);
        return forPerson(p.getId());
    }

    /** Also used by the phone's sync. */
    public List<RoutineResponse> forPerson(UUID personId) {
        return routine.findByPersonIdOrderByTimeAsc(personId).stream().map(RoutineResponse::of).toList();
    }

    @Transactional
    public RoutineResponse create(UUID personId, RoutineRequest req) {
        FamilyMember m = access.editor();
        Person p = people.load(m.getFamilyId(), personId);
        RoutineItem r = new RoutineItem(m.getFamilyId(), p.getId());
        apply(r, req);
        routine.save(r);
        events.publishEvent(new ScheduleChanged(p.getId()));
        return RoutineResponse.of(r);
    }

    @Transactional
    public RoutineResponse update(UUID id, RoutineRequest req) {
        RoutineItem r = load(access.editor().getFamilyId(), id);
        apply(r, req);
        events.publishEvent(new ScheduleChanged(r.getPersonId()));
        return RoutineResponse.of(r);
    }

    @Transactional
    public void delete(UUID id) {
        RoutineItem r = load(access.editor().getFamilyId(), id);
        routine.delete(r);
        events.publishEvent(new ScheduleChanged(r.getPersonId()));
    }

    private RoutineItem load(UUID familyId, UUID id) {
        return routine.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Routine item not found."));
    }

    private static void apply(RoutineItem r, RoutineRequest req) {
        String spoken = req.spokenText() == null || req.spokenText().isBlank() ? null : req.spokenText().trim();
        r.update(req.time(), req.days(), req.label().trim(), spoken, req.active() == null || req.active());
    }
}
