package app.medreminder.pairing;

import app.medreminder.TestApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PairingTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    PairingCodeRepository codes;
    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mvc, json);
    }

    private String redeemBody(String code, String personId) {
        return "{\"code\":\"%s\",\"personId\":\"%s\",\"deviceName\":\"Thatha's Redmi\",\"platform\":\"android\"}"
                .formatted(code, personId);
    }

    @Test
    void codeLooksLikeK7QM4XPA() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String code = api.newCode(editor, "DEVICE", null);
        assertThat(code).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
    }

    @Test
    void phonePairsWithCodeAndGetsItsOwnToken() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String thatha = api.newPerson(editor, "Thatha", "te");
        api.newPerson(editor, "Amma", "te");
        String code = api.newCode(editor, "DEVICE", null);

        // The phone (no login) looks up the code and sees who it can be. Lower case and no dash still work.
        api.post(null, "/api/pairing/lookup", "{\"code\":\"%s\"}".formatted(code.toLowerCase().replace("-", " ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyName").value("Reddy family"))
                .andExpect(jsonPath("$.purpose").value("DEVICE"))
                .andExpect(jsonPath("$.people.length()").value(2))
                .andExpect(jsonPath("$.people[0].name").value("Thatha"));

        var redeemed = api.read(api.post(null, "/api/pairing/redeem", redeemBody(code, thatha))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person.name").value("Thatha"))
                .andExpect(jsonPath("$.person.voiceLanguage").value("te")));
        String deviceToken = redeemed.get("deviceToken").asText();
        String deviceId = redeemed.get("deviceId").asText();

        // The phone can read its own info...
        api.get(deviceToken, "/api/devices/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.familyName").value("Reddy family"))
                .andExpect(jsonPath("$.person.name").value("Thatha"));
        // ...but cannot use family endpoints.
        api.get(deviceToken, "/api/people").andExpect(status().isForbidden());
        api.get(deviceToken, "/api/families/me").andExpect(status().isForbidden());
        // And a family login is not a phone.
        api.get(editor, "/api/devices/me").andExpect(status().isForbidden());

        // The family sees the linked phone.
        api.get(editor, "/api/people/" + thatha).andExpect(jsonPath("$.linkedPhones").value(1));
        api.get(editor, "/api/devices?personId=" + thatha).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Thatha's Redmi"))
                .andExpect(jsonPath("$[0].platform").value("android"));

        // Unlinking stops the phone's token at once.
        api.delete(editor, "/api/devices/" + deviceId).andExpect(status().isNoContent());
        api.get(deviceToken, "/api/devices/me").andExpect(status().isUnauthorized());
        api.get(editor, "/api/people/" + thatha).andExpect(jsonPath("$.linkedPhones").value(0));
    }

    @Test
    void codeWorksOnlyOnce() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String thatha = api.newPerson(editor, "Thatha", "te");
        String code = api.newCode(editor, "DEVICE", null);

        api.post(null, "/api/pairing/redeem", redeemBody(code, thatha)).andExpect(status().isCreated());
        api.post(null, "/api/pairing/redeem", redeemBody(code, thatha)).andExpect(status().isBadRequest());
        api.post(null, "/api/pairing/lookup", "{\"code\":\"%s\"}".formatted(code)).andExpect(status().isBadRequest());
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String thatha = api.newPerson(editor, "Thatha", "te");
        String code = api.newCode(editor, "DEVICE", null);

        PairingCode stored = codes.findByCodeHash(CodeGenerator.hash(code)).orElseThrow();
        assertThat(stored.getExpiresAt()).isBetween(Instant.now().plusSeconds(23 * 3600), Instant.now().plusSeconds(24 * 3600));
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        codes.save(stored);

        api.post(null, "/api/pairing/lookup", "{\"code\":\"%s\"}".formatted(code)).andExpect(status().isBadRequest());
        api.post(null, "/api/pairing/redeem", redeemBody(code, thatha)).andExpect(status().isBadRequest());
    }

    @Test
    void wrongCodeAndWrongPersonAreRejected() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String other = api.newEditorWithFamily("Other family");
        String strangerPerson = api.newPerson(other, "Stranger", "en");
        String code = api.newCode(editor, "DEVICE", null);

        api.post(null, "/api/pairing/lookup", "{\"code\":\"AAAA-BBBB\"}").andExpect(status().isBadRequest());
        api.post(null, "/api/pairing/lookup", "{\"code\":\"short\"}").andExpect(status().isBadRequest());
        // A person from another family cannot be chosen, and the code is not used up.
        api.post(null, "/api/pairing/redeem", redeemBody(code, strangerPerson)).andExpect(status().isBadRequest());
        api.post(null, "/api/pairing/lookup", "{\"code\":\"%s\"}".formatted(code)).andExpect(status().isOk());
    }

    @Test
    void codesOnlyWorkForTheirPurpose() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String thatha = api.newPerson(editor, "Thatha", "te");
        String memberCode = api.newCode(editor, "MEMBER", "EDITOR");
        String deviceCode = api.newCode(editor, "DEVICE", null);
        String newcomer = api.newUser("Chinna");

        api.post(null, "/api/pairing/redeem", redeemBody(memberCode, thatha)).andExpect(status().isBadRequest());
        api.post(newcomer, "/api/pairing/join", "{\"code\":\"%s\"}".formatted(deviceCode)).andExpect(status().isBadRequest());

        // Member lookup shows the family name but not the people.
        api.post(null, "/api/pairing/lookup", "{\"code\":\"%s\"}".formatted(memberCode))
                .andExpect(jsonPath("$.purpose").value("MEMBER"))
                .andExpect(jsonPath("$.people.length()").value(0));

        api.post(newcomer, "/api/pairing/join", "{\"code\":\"%s\"}".formatted(memberCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reddy family"))
                .andExpect(jsonPath("$.myRole").value("EDITOR"));
    }

    @Test
    void someoneAlreadyInAFamilyCannotJoinAndDoesNotWasteTheCode() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String busy = api.newEditorWithFamily("Their own family");
        String code = api.newCode(editor, "MEMBER", null);

        api.post(busy, "/api/pairing/join", "{\"code\":\"%s\"}".formatted(code)).andExpect(status().isConflict());
        api.post(api.newUser("Dad"), "/api/pairing/join", "{\"code\":\"%s\"}".formatted(code)).andExpect(status().isOk());
    }

    @Test
    void joiningNeedsALogin() throws Exception {
        api.post(null, "/api/pairing/join", "{\"code\":\"AAAA-BBBB\"}").andExpect(status().isUnauthorized());
        api.post(null, "/api/pairing/codes", "{\"purpose\":\"DEVICE\"}").andExpect(status().isUnauthorized());
    }
}
