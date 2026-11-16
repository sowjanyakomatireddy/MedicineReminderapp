package app.medreminder.medicine;

import app.medreminder.TestApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MedicineTest {
    /** Smallest valid PNG header; enough for type detection. */
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    TestApi api;
    String editor;
    String thatha;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mvc, json);
        editor = api.newEditorWithFamily("Reddy family");
        thatha = api.newPerson(editor, "Thatha", "te");
    }

    private String bpTablet(String personId) {
        return """
                {"personId":"%s","name":"Amlodipine 5 mg","voiceName":"బీపీ మాత్ర","shape":"ROUND","color":"WHITE",
                 "notes":"1 tablet",
                 "schedules":[{"time":"07:00","days":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
                               "food":"BEFORE"}]}
                """.formatted(personId);
    }

    private JsonNode create() throws Exception {
        return api.read(api.post(editor, "/api/medicines", bpTablet(thatha)).andExpect(status().isCreated()));
    }

    private ResultActions upload(String token, String path, byte[] bytes) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, path)
                .file(new MockMultipartFile("photo", "pill.png", "image/png", bytes))
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void createWithDefaultsAndReadBack() throws Exception {
        JsonNode med = create();
        assertThat(med.get("voiceName").asText()).isEqualTo("బీపీ మాత్ర");
        JsonNode s = med.get("schedules").get(0);
        assertThat(s.get("time").asText()).isEqualTo("07:00");
        assertThat(s.get("days")).hasSize(7);
        assertThat(s.get("remindBeforeMin").asInt()).isEqualTo(5);
        assertThat(s.get("repeatEveryMin").asInt()).isEqualTo(5);
        assertThat(s.get("stopAfterMin").asInt()).isEqualTo(30);
        assertThat(s.get("active").asBoolean()).isTrue();

        api.get(editor, "/api/medicines?personId=" + thatha).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Amlodipine 5 mg"));
        api.get(editor, "/api/people/" + thatha).andExpect(jsonPath("$.medicineCount").value(1));
    }

    @Test
    void updateKeepsScheduleIdsAddsAndRemovesTimes() throws Exception {
        JsonNode med = create();
        String id = med.get("id").asText();
        String morning = med.get("schedules").get(0).get("id").asText();

        // Keep the morning time (now after food, alarm settings changed), add a night time.
        JsonNode updated = api.read(api.put(editor, "/api/medicines/" + id, """
                {"name":"Amlodipine 5 mg","voiceName":"బీపీ మాత్ర",
                 "schedules":[
                   {"id":"%s","time":"07:30","days":["MONDAY"],"food":"AFTER","remindBeforeMin":10,"repeatEveryMin":2,"stopAfterMin":45,"active":false},
                   {"time":"21:00","days":["SUNDAY"],"food":"ANY"}]}
                """.formatted(morning)).andExpect(status().isOk()));
        assertThat(updated.get("schedules")).hasSize(2);
        JsonNode first = updated.get("schedules").get(0);
        assertThat(first.get("id").asText()).isEqualTo(morning);
        assertThat(first.get("time").asText()).isEqualTo("07:30");
        assertThat(first.get("food").asText()).isEqualTo("AFTER");
        assertThat(first.get("remindBeforeMin").asInt()).isEqualTo(10);
        assertThat(first.get("active").asBoolean()).isFalse();
        assertThat(updated.has("shape")).as("shape cleared (nulls are left out of JSON)").isFalse();

        // Drop the morning time.
        String night = updated.get("schedules").get(1).get("id").asText();
        api.put(editor, "/api/medicines/" + id, """
                {"name":"Amlodipine 5 mg","schedules":[{"id":"%s","time":"21:00","days":["SUNDAY"],"food":"ANY"}]}
                """.formatted(night)).andExpect(status().isOk()).andExpect(jsonPath("$.schedules.length()").value(1));
    }

    @Test
    void validation() throws Exception {
        api.post(editor, "/api/medicines", "{\"personId\":\"%s\",\"name\":\"\",\"schedules\":[]}".formatted(thatha))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.schedules").exists());
        api.post(editor, "/api/medicines", """
                {"personId":"%s","name":"X","schedules":[{"time":"07:00","days":[],"food":"BEFORE","repeatEveryMin":0}]}
                """.formatted(thatha))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['schedules[0].days']").exists())
                .andExpect(jsonPath("$.errors['schedules[0].repeatEveryMin']").exists());
        api.post(editor, "/api/medicines", """
                {"personId":"%s","name":"X","schedules":[{"time":"25:00","days":["MONDAY"],"food":"BEFORE"}]}
                """.formatted(thatha)).andExpect(status().isBadRequest());
    }

    @Test
    void scheduleIdFromAnotherMedicineIsRejected() throws Exception {
        String a = create().get("id").asText();
        String otherSchedule = create().get("schedules").get(0).get("id").asText();
        api.put(editor, "/api/medicines/" + a, """
                {"name":"X","schedules":[{"id":"%s","time":"07:00","days":["MONDAY"],"food":"ANY"}]}
                """.formatted(otherSchedule)).andExpect(status().isBadRequest());
    }

    @Test
    void otherFamiliesAndViewersAreKeptOut() throws Exception {
        String id = create().get("id").asText();
        String stranger = api.newEditorWithFamily("Other family");
        api.get(stranger, "/api/medicines/" + id).andExpect(status().isNotFound());
        api.get(stranger, "/api/medicines").andExpect(jsonPath("$.length()").value(0));
        api.delete(stranger, "/api/medicines/" + id).andExpect(status().isNotFound());
        // Cannot add a medicine for someone else's person.
        api.post(stranger, "/api/medicines", bpTablet(thatha)).andExpect(status().isNotFound());

        String viewer = api.newUser("Dad");
        api.post(viewer, "/api/pairing/join", "{\"code\":\"%s\"}".formatted(api.newCode(editor, "MEMBER", "VIEWER")));
        api.get(viewer, "/api/medicines/" + id).andExpect(status().isOk());
        api.post(viewer, "/api/medicines", bpTablet(thatha)).andExpect(status().isForbidden());
        api.delete(viewer, "/api/medicines/" + id).andExpect(status().isForbidden());
    }

    @Test
    void photoUploadServeReplaceAndDelete() throws Exception {
        String id = create().get("id").asText();
        api.get(editor, "/api/medicines/" + id + "/photo").andExpect(status().isNotFound());

        JsonNode withPhoto = api.read(upload(editor, "/api/medicines/" + id + "/photo", PNG).andExpect(status().isOk()));
        assertThat(withPhoto.get("hasPhoto").asBoolean()).isTrue();
        String v1 = withPhoto.get("photoVersion").asText();

        api.get(editor, "/api/medicines/" + id + "/photo").andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));

        String v2 = api.read(upload(editor, "/api/medicines/" + id + "/photo", PNG)).get("photoVersion").asText();
        assertThat(v2).isNotEqualTo(v1);

        api.delete(editor, "/api/medicines/" + id + "/photo").andExpect(status().isNoContent());
        api.get(editor, "/api/medicines/" + id).andExpect(jsonPath("$.hasPhoto").value(false));
    }

    @Test
    void photoMustBeARealImageAndFromThisFamily() throws Exception {
        String id = create().get("id").asText();
        // A script renamed to .png is refused: the type comes from the bytes, not the name.
        upload(editor, "/api/medicines/" + id + "/photo", "<script>alert(1)</script>".getBytes())
                .andExpect(status().isBadRequest());
        String stranger = api.newEditorWithFamily("Other family");
        upload(stranger, "/api/medicines/" + id + "/photo", PNG).andExpect(status().isNotFound());
        upload(editor, "/api/medicines/" + id + "/photo", PNG).andExpect(status().isOk());
        api.get(stranger, "/api/medicines/" + id + "/photo").andExpect(status().isNotFound());
    }

    @Test
    void personPhoto() throws Exception {
        upload(editor, "/api/people/" + thatha + "/photo", PNG).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(true));
        api.get(editor, "/api/people/" + thatha + "/photo").andExpect(status().isOk());
    }

    @Test
    void deletingAPersonRemovesTheirMedicines() throws Exception {
        String id = create().get("id").asText();
        api.delete(editor, "/api/people/" + thatha).andExpect(status().isNoContent());
        api.get(editor, "/api/medicines/" + id).andExpect(status().isNotFound());
    }

    @Test
    void todayEndpointListsEachPerson() throws Exception {
        create();
        api.get(editor, "/api/families/me/today").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Thatha"))
                .andExpect(jsonPath("$[0].total").value(1))
                .andExpect(jsonPath("$[0].doses[0].voiceName").value("బీపీ మాత్ర"))
                .andExpect(jsonPath("$[0].doses[0].alertAt").value("06:55"))
                .andExpect(jsonPath("$[0].doses[0].food").value("BEFORE"));
    }
}
