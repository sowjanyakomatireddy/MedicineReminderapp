package app.medreminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Small helper so tests read like the real flow: register, create a family, add people, pair a phone. */
public class TestApi {
    private final MockMvc mvc;
    private final ObjectMapper json;

    public TestApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** Registers a new user and returns their bearer token. */
    public String newUser(String name) throws Exception {
        String email = "u-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"email":"%s","password":"secret123","displayName":"%s","appLanguage":"en"}
                """.formatted(email, name);
        return read(post(null, "/api/auth/register", body).andExpect(status().isCreated())).get("token").asText();
    }

    /** Registers a user who creates a family; returns their token. */
    public String newEditorWithFamily(String familyName) throws Exception {
        String token = newUser("Editor");
        post(token, "/api/families", "{\"name\":\"%s\"}".formatted(familyName)).andExpect(status().isCreated());
        return token;
    }

    public String newPerson(String token, String name, String lang) throws Exception {
        return read(post(token, "/api/people", "{\"name\":\"%s\",\"voiceLanguage\":\"%s\"}".formatted(name, lang))
                .andExpect(status().isCreated())).get("id").asText();
    }

    public String newCode(String token, String purpose, String role) throws Exception {
        String body = role == null
                ? "{\"purpose\":\"%s\"}".formatted(purpose)
                : "{\"purpose\":\"%s\",\"role\":\"%s\"}".formatted(purpose, role);
        return read(post(token, "/api/pairing/codes", body).andExpect(status().isCreated())).get("code").asText();
    }

    /** Links a phone to the person and returns its device token. */
    public String pairPhone(String editorToken, String personId) throws Exception {
        String code = newCode(editorToken, "DEVICE", null);
        String body = "{\"code\":\"%s\",\"personId\":\"%s\",\"platform\":\"android\"}".formatted(code, personId);
        return read(post(null, "/api/pairing/redeem", body).andExpect(status().isCreated())).get("deviceToken").asText();
    }

    /** A medicine with one daily time; returns the medicine JSON. */
    public JsonNode newMedicine(String token, String personId, String time) throws Exception {
        String body = """
                {"personId":"%s","name":"Amlodipine 5 mg","voiceName":"బీపీ మాత్ర",
                 "schedules":[{"time":"%s","days":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
                               "food":"BEFORE"}]}
                """.formatted(personId, time);
        return read(post(token, "/api/medicines", body).andExpect(status().isCreated()));
    }

    public ResultActions get(String token, String path) throws Exception {
        return mvc.perform(auth(token, MockMvcRequestBuilders.get(path)));
    }

    public ResultActions post(String token, String path, String body) throws Exception {
        return mvc.perform(auth(token, MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    public ResultActions put(String token, String path, String body) throws Exception {
        return mvc.perform(auth(token, MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    public ResultActions patch(String token, String path, String body) throws Exception {
        return mvc.perform(auth(token, MockMvcRequestBuilders.patch(path).contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    public ResultActions delete(String token, String path) throws Exception {
        return mvc.perform(auth(token, MockMvcRequestBuilders.delete(path)));
    }

    public JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder auth(String token, MockHttpServletRequestBuilder b) {
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }
}
