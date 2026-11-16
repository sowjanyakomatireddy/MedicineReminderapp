package app.medreminder.family;

import app.medreminder.TestApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FamilyAndPeopleTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mvc, json);
    }

    @Test
    void newUserHasNoFamilyUntilTheyCreateOne() throws Exception {
        String t = api.newUser("Sowjanya");
        api.get(t, "/api/families/me").andExpect(status().isNotFound());

        api.post(t, "/api/families", "{\"name\":\"Reddy family\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Reddy family"))
                .andExpect(jsonPath("$.myRole").value("EDITOR"));
        api.get(t, "/api/families/me").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Reddy family"));

        // One family per user.
        api.post(t, "/api/families", "{\"name\":\"Another\"}").andExpect(status().isConflict());
    }

    @Test
    void editorManagesPeople() throws Exception {
        String t = api.newEditorWithFamily("Reddy family");
        String id = api.newPerson(t, "Thatha", "te");

        api.get(t, "/api/people").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Thatha"))
                .andExpect(jsonPath("$[0].voiceLanguage").value("te"))
                .andExpect(jsonPath("$[0].timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$[0].linkedPhones").value(0));

        api.put(t, "/api/people/" + id, "{\"name\":\"Paati\",\"voiceLanguage\":\"ta\",\"timeZone\":\"Asia/Dubai\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceLanguage").value("ta"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Dubai"));

        api.delete(t, "/api/people/" + id).andExpect(status().isNoContent());
        api.get(t, "/api/people/" + id).andExpect(status().isNotFound());
    }

    @Test
    void personInputIsValidated() throws Exception {
        String t = api.newEditorWithFamily("Reddy family");
        api.post(t, "/api/people", "{\"name\":\"\",\"voiceLanguage\":\"Telugu\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.voiceLanguage").exists());
        api.post(t, "/api/people", "{\"name\":\"Thatha\",\"voiceLanguage\":\"te\",\"timeZone\":\"Mars/Base\"}")
                .andExpect(status().isBadRequest());
        api.get(t, "/api/people/not-a-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void familiesCannotSeeEachOther() throws Exception {
        String a = api.newEditorWithFamily("Family A");
        String b = api.newEditorWithFamily("Family B");
        String thatha = api.newPerson(a, "Thatha", "te");

        api.get(b, "/api/people").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        api.get(b, "/api/people/" + thatha).andExpect(status().isNotFound());
        api.put(b, "/api/people/" + thatha, "{\"name\":\"X\",\"voiceLanguage\":\"en\"}").andExpect(status().isNotFound());
        api.delete(b, "/api/people/" + thatha).andExpect(status().isNotFound());
    }

    @Test
    void viewerCanReadButNotChange() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String thatha = api.newPerson(editor, "Thatha", "te");
        String viewer = api.newUser("Dad");
        api.post(viewer, "/api/pairing/join", "{\"code\":\"%s\"}".formatted(api.newCode(editor, "MEMBER", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("VIEWER"));

        api.get(viewer, "/api/people").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        api.post(viewer, "/api/people", "{\"name\":\"X\",\"voiceLanguage\":\"en\"}").andExpect(status().isForbidden());
        api.put(viewer, "/api/people/" + thatha, "{\"name\":\"X\",\"voiceLanguage\":\"en\"}").andExpect(status().isForbidden());
        api.delete(viewer, "/api/people/" + thatha).andExpect(status().isForbidden());
        api.post(viewer, "/api/pairing/codes", "{\"purpose\":\"DEVICE\"}").andExpect(status().isForbidden());
        api.patch(viewer, "/api/families/me", "{\"name\":\"Mine now\"}").andExpect(status().isForbidden());
    }

    @Test
    void membersListRolesAndLastEditorRule() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String dad = api.newUser("Dad");
        api.post(dad, "/api/pairing/join", "{\"code\":\"%s\"}".formatted(api.newCode(editor, "MEMBER", null)))
                .andExpect(status().isOk());

        var members = api.read(api.get(editor, "/api/families/me/members")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("EDITOR"))
                .andExpect(jsonPath("$[0].me").value(true))
                .andExpect(jsonPath("$[1].displayName").value("Dad"))
                .andExpect(jsonPath("$[1].role").value("VIEWER"))
                .andExpect(jsonPath("$[1].me").value(false)));
        String editorMemberId = members.get(0).get("id").asText();
        String dadMemberId = members.get(1).get("id").asText();

        // The only editor cannot step down or leave.
        api.patch(editor, "/api/families/me/members/" + editorMemberId, "{\"role\":\"VIEWER\"}")
                .andExpect(status().isConflict());
        api.delete(editor, "/api/families/me/members/" + editorMemberId).andExpect(status().isConflict());

        // A viewer cannot remove someone else, but can leave.
        api.delete(dad, "/api/families/me/members/" + editorMemberId).andExpect(status().isForbidden());

        // Promote Dad; now the first editor may step down.
        api.patch(editor, "/api/families/me/members/" + dadMemberId, "{\"role\":\"EDITOR\"}")
                .andExpect(status().isNoContent());
        api.patch(editor, "/api/families/me/members/" + editorMemberId, "{\"role\":\"VIEWER\"}")
                .andExpect(status().isNoContent());

        api.delete(editor, "/api/families/me/members/" + editorMemberId).andExpect(status().isNoContent());
        api.get(editor, "/api/families/me").andExpect(status().isNotFound());
    }

    @Test
    void unknownUrlIs404NotServerError() throws Exception {
        String t = api.newUser("X");
        api.get(t, "/api/does-not-exist").andExpect(status().isNotFound());
    }
}
