package app.medreminder.dose;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoseFlowTest {
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    DoseLogService doses;
    TestApi api;
    String editor;
    String thatha;
    String phone;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mvc, json);
        editor = api.newEditorWithFamily("Reddy family");
        thatha = api.newPerson(editor, "Thatha", "te");
        phone = api.pairPhone(editor, thatha);
    }

    /** A dose 10 minutes from now, so its whole window is in the future (and the schedule existed before it). */
    private ZonedDateTime soon() {
        return ZonedDateTime.now(IST).plusMinutes(10).withSecond(0).withNano(0);
    }

    private String log(String id, String scheduleId, LocalDate date, String status) {
        return "{\"id\":\"%s\",\"scheduleId\":\"%s\",\"date\":\"%s\",\"status\":\"%s\",\"at\":%d}"
                .formatted(id, scheduleId, date, status, System.currentTimeMillis());
    }

    @Test
    void phoneSyncHasPersonMedicinesAndLogs() throws Exception {
        api.put(editor, "/api/people/" + thatha,
                "{\"name\":\"Thatha\",\"voiceLanguage\":\"te\",\"familyPhone\":\"+91 98765 43210\",\"phone\":\"+91 90000 00000\"}")
                .andExpect(status().isOk());
        JsonNode med = api.newMedicine(editor, thatha, "07:00");
        String scheduleId = med.get("schedules").get(0).get("id").asText();

        api.get(phone, "/api/devices/me/sync").andExpect(status().isOk())
                .andExpect(jsonPath("$.person.name").value("Thatha"))
                .andExpect(jsonPath("$.person.voiceLanguage").value("te"))
                .andExpect(jsonPath("$.person.familyPhone").value("+91 98765 43210"))
                .andExpect(jsonPath("$.familyName").value("Reddy family"))
                .andExpect(jsonPath("$.medicines[0].voiceName").value("బీపీ మాత్ర"))
                .andExpect(jsonPath("$.medicines[0].schedules[0].id").value(scheduleId))
                .andExpect(jsonPath("$.pushEnabled").value(false));

        // The family sees when the phone last synced.
        api.get(editor, "/api/devices?personId=" + thatha).andExpect(jsonPath("$[0].lastSyncAt").exists());
        // Family logins cannot use phone endpoints.
        api.get(editor, "/api/devices/me/sync").andExpect(status().isForbidden());
        api.put(phone, "/api/devices/me/push-token", "{\"token\":\"fcm-token-1\"}").andExpect(status().isNoContent());
    }

    @Test
    void phoneLogsAreIdempotentAndShowOnFamilyHome() throws Exception {
        String scheduleId = api.newMedicine(editor, thatha, "07:00").get("schedules").get(0).get("id").asText();
        LocalDate today = LocalDate.now(IST);
        String id = UUID.randomUUID().toString();
        String batch = "[" + log(id, scheduleId, today, "TAKEN") + ","
                + log(UUID.randomUUID().toString(), "sample-daily", today, "MISSED") + "]";

        api.post(phone, "/api/devices/me/dose-logs", batch).andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted.length()").value(2)); // the sample alarm is accepted and dropped
        api.post(phone, "/api/devices/me/dose-logs", batch).andExpect(status().isOk()); // same again: no change

        api.get(editor, "/api/families/me/today").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taken").value(1))
                .andExpect(jsonPath("$[0].doses[0].status").value("TAKEN"));
        api.get(phone, "/api/devices/me/sync")
                .andExpect(jsonPath("$.logs.length()").value(1))
                .andExpect(jsonPath("$.logs[0].status").value("TAKEN"));
    }

    @Test
    void lateTakenReplacesMissedButNotTheOtherWay() throws Exception {
        String scheduleId = api.newMedicine(editor, thatha, "07:00").get("schedules").get(0).get("id").asText();
        LocalDate today = LocalDate.now(IST);
        api.post(phone, "/api/devices/me/dose-logs", "[" + log(UUID.randomUUID().toString(), scheduleId, today, "MISSED") + "]");
        api.get(editor, "/api/families/me/today").andExpect(jsonPath("$[0].doses[0].status").value("MISSED"));

        api.post(phone, "/api/devices/me/dose-logs", "[" + log(UUID.randomUUID().toString(), scheduleId, today, "TAKEN") + "]");
        api.get(editor, "/api/families/me/today").andExpect(jsonPath("$[0].doses[0].status").value("TAKEN"));

        api.post(phone, "/api/devices/me/dose-logs", "[" + log(UUID.randomUUID().toString(), scheduleId, today, "MISSED") + "]");
        api.get(editor, "/api/families/me/today").andExpect(jsonPath("$[0].doses[0].status").value("TAKEN"));
    }

    @Test
    void anotherPersonsScheduleIsIgnored() throws Exception {
        String paati = api.newPerson(editor, "Paati", "ta");
        String paatiSchedule = api.newMedicine(editor, paati, "07:00").get("schedules").get(0).get("id").asText();
        api.post(phone, "/api/devices/me/dose-logs",
                        "[" + log(UUID.randomUUID().toString(), paatiSchedule, LocalDate.now(IST), "TAKEN") + "]")
                .andExpect(jsonPath("$.accepted.length()").value(1));
        api.get(editor, "/api/families/me/today").andExpect(jsonPath("$[1].taken").value(0));
    }

    @Test
    void serverMarksMissedAfterTheAutomaticStopAndGrace() throws Exception {
        ZonedDateTime dose = soon();
        String time = dose.format(DateTimeFormatter.ofPattern("HH:mm"));
        api.newMedicine(editor, thatha, time);
        Instant stop = dose.plusMinutes(30).toInstant(); // default stopAfterMin = 30

        assertThat(doses.markMissed(stop)).as("still within the 2-minute grace").isZero();
        assertThat(doses.markMissed(stop.plusSeconds(121))).isEqualTo(1);
        assertThat(doses.markMissed(stop.plusSeconds(180))).as("only once").isZero();

        JsonNode alerts = api.read(api.get(editor, "/api/dose-logs/alerts").andExpect(status().isOk()));
        JsonNode missed = null;
        for (JsonNode a : alerts) {
            if (a.get("personName").asText().equals("Thatha")) missed = a;
        }
        assertThat(missed).isNotNull();
        assertThat(missed.get("status").asText()).isEqualTo("MISSED");
        assertThat(missed.get("source").asText()).isEqualTo("SERVER");
        assertThat(missed.get("rings").asInt()).isEqualTo(7); // every 5 min from 5 before to 30 after
        String id = missed.get("id").asText();

        // Replay needs push, which is off in tests.
        api.post(editor, "/api/dose-logs/" + id + "/replay", "").andExpect(status().isServiceUnavailable());

        // Any family member can mark it taken.
        String viewer = api.newUser("Dad");
        api.post(viewer, "/api/pairing/join", "{\"code\":\"%s\"}".formatted(api.newCode(editor, "MEMBER", "VIEWER")));
        api.post(viewer, "/api/dose-logs/" + id + "/taken", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TAKEN"))
                .andExpect(jsonPath("$.source").value("FAMILY"));

        // Other families cannot see it.
        String stranger = api.newEditorWithFamily("Other");
        api.get(stranger, "/api/dose-logs/" + id).andExpect(status().isNotFound());
        api.post(stranger, "/api/dose-logs/" + id + "/taken", "").andExpect(status().isNotFound());
    }

    @Test
    void aTimeAddedAfterTheDoseStartedIsNotMissedToday() throws Exception {
        // 07:00 added "now": if 07:00 today has passed, it must not count as missed today.
        ZonedDateTime past = ZonedDateTime.now(IST).minusHours(2).withSecond(0).withNano(0);
        api.newMedicine(editor, thatha, past.format(DateTimeFormatter.ofPattern("HH:mm")));
        assertThat(doses.markMissed(Instant.now())).isZero();
    }

    @Test
    void weekSummary() throws Exception {
        api.newMedicine(editor, thatha, "07:00");
        api.get(editor, "/api/people/" + thatha + "/week").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[6].date").value(LocalDate.now(IST).toString()))
                .andExpect(jsonPath("$[6].total").value(1));
    }

    @Test
    void familyPushTokens() throws Exception {
        api.post(editor, "/api/me/push-tokens", "{\"token\":\"web-token-1\"}").andExpect(status().isNoContent());
        api.post(editor, "/api/me/push-tokens", "{\"token\":\"web-token-1\"}").andExpect(status().isNoContent());
        api.delete(editor, "/api/me/push-tokens").andExpect(status().isBadRequest()); // needs the token
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/me/push-tokens")
                        .header("Authorization", "Bearer " + editor)
                        .contentType("application/json").content("{\"token\":\"web-token-1\"}"))
                .andExpect(status().isNoContent());
        api.post(phone, "/api/me/push-tokens", "{\"token\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void phoneNumbersAreValidated() throws Exception {
        api.put(editor, "/api/people/" + thatha, "{\"name\":\"Thatha\",\"voiceLanguage\":\"te\",\"phone\":\"call me\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
    }
}
