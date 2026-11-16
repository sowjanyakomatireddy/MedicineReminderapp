package app.medreminder.routine;

import app.medreminder.TestApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoutineAndPeriodTest {
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final String EVERY_DAY = "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"]";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    TestApi api;
    String editor;
    String amma;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mvc, json);
        editor = api.newEditorWithFamily("Reddy family");
        amma = api.newPerson(editor, "అమ్మ", "te");
    }

    private String water() {
        return "{\"time\":\"06:30\",\"days\":%s,\"label\":\"Drink a glass of water\",\"spokenText\":\"నీళ్ళు తాగండి\"}"
                .formatted(EVERY_DAY);
    }

    @Test
    void routineItemsCrudAndValidation() throws Exception {
        String id = api.read(api.post(editor, "/api/people/" + amma + "/routine", water())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.time").value("06:30"))
                .andExpect(jsonPath("$.spokenText").value("నీళ్ళు తాగండి"))
                .andExpect(jsonPath("$.active").value(true))).get("id").asText();

        api.post(editor, "/api/people/" + amma + "/routine",
                        "{\"time\":\"17:00\",\"days\":[\"SUNDAY\"],\"label\":\"Call a relative\",\"spokenText\":\"  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spokenText").doesNotExist()); // blank → the voice reads the label

        api.get(editor, "/api/people/" + amma + "/routine").andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].label").value("Drink a glass of water")); // sorted by time

        api.put(editor, "/api/routine/" + id,
                        "{\"time\":\"07:00\",\"days\":[\"MONDAY\"],\"label\":\"Water\",\"active\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        api.post(editor, "/api/people/" + amma + "/routine", "{\"time\":\"07:00\",\"days\":[],\"label\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.days").exists())
                .andExpect(jsonPath("$.errors.label").exists());

        api.delete(editor, "/api/routine/" + id).andExpect(status().isNoContent());
        api.get(editor, "/api/people/" + amma + "/routine").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void viewersAndOtherFamilies() throws Exception {
        String id = api.read(api.post(editor, "/api/people/" + amma + "/routine", water())).get("id").asText();
        String viewer = api.newUser("Dad");
        api.post(viewer, "/api/pairing/join", "{\"code\":\"%s\"}".formatted(api.newCode(editor, "MEMBER", "VIEWER")));
        api.get(viewer, "/api/people/" + amma + "/routine").andExpect(status().isOk());
        api.post(viewer, "/api/people/" + amma + "/routine", water()).andExpect(status().isForbidden());
        api.delete(viewer, "/api/routine/" + id).andExpect(status().isForbidden());

        // Viewers may record a period start, but not change the settings.
        api.post(viewer, "/api/people/" + amma + "/period/starts", "").andExpect(status().isOk());
        api.put(viewer, "/api/people/" + amma + "/period", settings(true)).andExpect(status().isForbidden());

        String stranger = api.newEditorWithFamily("Other");
        api.get(stranger, "/api/people/" + amma + "/routine").andExpect(status().isNotFound());
        api.get(stranger, "/api/people/" + amma + "/period").andExpect(status().isNotFound());
        api.delete(stranger, "/api/routine/" + id).andExpect(status().isNotFound());
    }

    private String settings(boolean on) {
        return "{\"enabled\":%s,\"cycleLengthDays\":28,\"periodLengthDays\":5,\"remindDaysBefore\":2,\"remindTime\":\"09:00\"}"
                .formatted(on);
    }

    @Test
    void periodOffByDefaultThenEstimatesFromRecordedDates() throws Exception {
        api.get(editor, "/api/people/" + amma + "/period").andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.nextExpected").doesNotExist());
        api.put(editor, "/api/people/" + amma + "/period", settings(true)).andExpect(status().isOk());
        api.get(editor, "/api/people/" + amma).andExpect(jsonPath("$.periodTracking").value(true));

        LocalDate today = LocalDate.now(IST);
        api.post(editor, "/api/people/" + amma + "/period/starts", "{\"date\":\"%s\"}".formatted(today.minusDays(56)));
        api.post(editor, "/api/people/" + amma + "/period/starts", "{\"date\":\"%s\"}".formatted(today.minusDays(26)));
        JsonNode p = api.read(api.get(editor, "/api/people/" + amma + "/period"));
        assertThat(p.get("averageCycleDays").asInt()).isEqualTo(30);
        assertThat(p.get("lastStart").asText()).isEqualTo(today.minusDays(26).toString());
        assertThat(p.get("nextExpected").asText()).isEqualTo(today.plusDays(4).toString());
        assertThat(p.get("daysUntil").asInt()).isEqualTo(4);
        assertThat(p.get("reminderDate").asText()).isEqualTo(today.plusDays(2).toString());

        // "Period started today" with no date uses today in her time zone; twice is harmless.
        api.post(editor, "/api/people/" + amma + "/period/starts", "").andExpect(jsonPath("$.lastStart").value(today.toString()));
        api.post(editor, "/api/people/" + amma + "/period/starts", "").andExpect(jsonPath("$.recentStarts.length()").value(3));
        api.post(editor, "/api/people/" + amma + "/period/starts", "{\"date\":\"%s\"}".formatted(today.plusDays(1)))
                .andExpect(status().isBadRequest());
        api.delete(editor, "/api/people/" + amma + "/period/starts/" + today).andExpect(jsonPath("$.recentStarts.length()").value(2));
    }

    @Test
    void thePhoneGetsItsRoutineAndThePeriodReminder() throws Exception {
        String phone = api.pairPhone(editor, amma);
        api.post(editor, "/api/people/" + amma + "/routine", water());
        api.get(phone, "/api/devices/me/sync")
                .andExpect(jsonPath("$.routine.length()").value(1))
                .andExpect(jsonPath("$.routine[0].spokenText").value("నీళ్ళు తాగండి"))
                .andExpect(jsonPath("$.period").doesNotExist()); // off

        api.put(editor, "/api/people/" + amma + "/period", settings(true));
        LocalDate today = LocalDate.now(IST);
        api.post(editor, "/api/people/" + amma + "/period/starts", "{\"date\":\"%s\"}".formatted(today.minusDays(25)));
        api.get(phone, "/api/devices/me/sync")
                .andExpect(jsonPath("$.period.date").value(today.plusDays(1).toString())) // 28 - 25 - 2
                .andExpect(jsonPath("$.period.time").value("09:00"))
                .andExpect(jsonPath("$.period.expected").value(today.plusDays(3).toString()));

        // Turning it off removes the reminder from the phone.
        api.put(editor, "/api/people/" + amma + "/period", settings(false));
        api.get(phone, "/api/devices/me/sync").andExpect(jsonPath("$.period").doesNotExist());
    }
}
