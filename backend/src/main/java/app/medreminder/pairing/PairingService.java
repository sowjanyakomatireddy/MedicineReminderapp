package app.medreminder.pairing;

import app.medreminder.common.ApiException;
import app.medreminder.device.Device;
import app.medreminder.device.DeviceRepository;
import app.medreminder.family.FamilyAccess;
import app.medreminder.family.FamilyDtos.FamilyResponse;
import app.medreminder.family.FamilyMember;
import app.medreminder.family.FamilyService;
import app.medreminder.family.Role;
import app.medreminder.pairing.PairingDtos.CodeResponse;
import app.medreminder.pairing.PairingDtos.CreateCodeRequest;
import app.medreminder.pairing.PairingDtos.LookupResponse;
import app.medreminder.pairing.PairingDtos.PersonChoice;
import app.medreminder.pairing.PairingDtos.RedeemRequest;
import app.medreminder.pairing.PairingDtos.RedeemResponse;
import app.medreminder.person.Person;
import app.medreminder.person.PersonRepository;
import app.medreminder.person.PersonService;
import app.medreminder.security.CurrentUser;
import app.medreminder.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class PairingService {
    static final Duration CODE_LIFETIME = Duration.ofHours(24);
    /** Same message for wrong, used, expired or wrong-purpose codes, so callers learn nothing extra. */
    static final String INVALID = "This code is wrong, already used, or expired. Ask the family for a new code.";

    private final PairingCodeRepository codes;
    private final CodeGenerator generator;
    private final FamilyAccess access;
    private final FamilyService families;
    private final PersonRepository people;
    private final PersonService personService;
    private final DeviceRepository devices;
    private final JwtService jwt;

    public PairingService(PairingCodeRepository codes, CodeGenerator generator, FamilyAccess access,
                          FamilyService families, PersonRepository people, PersonService personService,
                          DeviceRepository devices, JwtService jwt) {
        this.codes = codes;
        this.generator = generator;
        this.access = access;
        this.families = families;
        this.people = people;
        this.personService = personService;
        this.devices = devices;
        this.jwt = jwt;
    }

    /** An editor makes a code: DEVICE to link a phone, MEMBER to invite a family member. */
    @Transactional
    public CodeResponse create(CreateCodeRequest req) {
        FamilyMember editor = access.editor();
        Role role = req.purpose() == PairingPurpose.MEMBER
                ? (req.role() == null ? Role.VIEWER : req.role())
                : null;
        String code;
        do {
            code = generator.newCode();
        } while (codes.findByCodeHash(CodeGenerator.hash(code)).isPresent());
        PairingCode saved = codes.save(new PairingCode(editor.getFamilyId(), CodeGenerator.hash(code), req.purpose(),
                role, editor.getUserId(), Instant.now().plus(CODE_LIFETIME)));
        return new CodeResponse(code, saved.getPurpose(), saved.getRole(), saved.getExpiresAt());
    }

    /** Public (no login). Does not use up the code. */
    @Transactional(readOnly = true)
    public LookupResponse lookup(String code) {
        PairingCode c = findUsable(code);
        List<PersonChoice> choices = c.getPurpose() == PairingPurpose.DEVICE
                ? people.findByFamilyIdOrderByCreatedAt(c.getFamilyId()).stream()
                        .map(p -> new PersonChoice(p.getId(), p.getName(), p.getVoiceLanguage())).toList()
                : List.of();
        return new LookupResponse(families.load(c.getFamilyId()).getName(), c.getPurpose(), choices);
    }

    /** Public (no login). Links this phone to a person and returns its device token. */
    @Transactional
    public RedeemResponse redeem(RedeemRequest req) {
        PairingCode c = findUsable(req.code(), PairingPurpose.DEVICE);
        Person person = people.findByIdAndFamilyId(req.personId(), c.getFamilyId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Choose a person from this family."));
        consume(c);
        String platform = req.platform() == null ? "web" : req.platform();
        String name = req.deviceName() == null || req.deviceName().isBlank() ? null : req.deviceName().trim();
        Device d = devices.save(new Device(c.getFamilyId(), person.getId(), name, platform));
        return new RedeemResponse(jwt.issueDeviceToken(d.getId()), d.getId(),
                families.load(c.getFamilyId()).getName(), personService.toResponse(person));
    }

    /** A logged-in family member joins the family with a MEMBER code. */
    @Transactional
    public FamilyResponse join(String code) {
        PairingCode c = findUsable(code, PairingPurpose.MEMBER);
        families.ensureNotInFamily(CurrentUser.get().userId()); // check first, so the code is not wasted
        consume(c);
        return families.join(c.getFamilyId(), c.getRole() == null ? Role.VIEWER : c.getRole());
    }

    private PairingCode findUsable(String code, PairingPurpose purpose) {
        PairingCode c = findUsable(code);
        if (c.getPurpose() != purpose) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID);
        }
        return c;
    }

    private PairingCode findUsable(String code) {
        if (CodeGenerator.normalize(code).length() != CodeGenerator.LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID);
        }
        return codes.findByCodeHash(CodeGenerator.hash(code))
                .filter(c -> c.isUsable(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, INVALID));
    }

    /** Atomic: if two phones redeem the same code at once, only one wins. */
    private void consume(PairingCode c) {
        if (codes.markUsed(c.getId(), Instant.now()) != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID);
        }
    }
}
