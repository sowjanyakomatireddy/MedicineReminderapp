package app.medreminder.dose;

import app.medreminder.TestApi;
import app.medreminder.push.PushSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Which pushes go where, with a fake Firebase. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushMessagesTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    DoseLogService doses;
    @MockitoBean
    PushSender push;
    TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mvc, json);
        when(push.enabled()).thenReturn(true);
        when(push.toPhone(anyString(), anyMap())).thenReturn(PushSender.Result.SENT);
        when(push.toFamily(anyString(), anyString(), anyString(), anyString(), anyMap())).thenReturn(PushSender.Result.SENT);
    }

    @Test
    void savingAMedicineWakesThePhoneToSync() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String thatha = api.newPerson(editor, "Thatha", "te");
        String phone = api.pairPhone(editor, thatha);
        api.put(phone, "/api/devices/me/push-token", "{\"token\":\"phone-1\"}").andExpect(status().isNoContent());

        api.newMedicine(editor, thatha, "07:00");
        verify(push, timeout(2000)).toPhone("phone-1", Map.of("type", "SYNC"));
    }

    @Test
    void missedDoseAlertsTheFamilyInTheirLanguageAndReplayRingsThePhone() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family"); // registered with app language "en"
        String thatha = api.newPerson(editor, "తాతయ్య", "te");
        String phone = api.pairPhone(editor, thatha);
        api.put(phone, "/api/devices/me/push-token", "{\"token\":\"phone-1\"}");
        api.post(editor, "/api/me/push-tokens", "{\"token\":\"web-1\"}").andExpect(status().isNoContent());

        ZonedDateTime dose = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).plusMinutes(10).withSecond(0).withNano(0);
        String scheduleId = api.newMedicine(editor, thatha, dose.format(DateTimeFormatter.ofPattern("HH:mm")))
                .get("schedules").get(0).get("id").asText();
        clearInvocations(push);

        doses.markMissed(dose.plusMinutes(33).toInstant());
        verify(push, timeout(2000)).toFamily(eq("web-1"), eq("తాతయ్య has not taken a medicine"),
                contains("బీపీ మాత్ర"), contains("/missed/"), anyMap());

        String id = api.read(api.get(editor, "/api/dose-logs/alerts")).get(0).get("id").asText();
        api.post(editor, "/api/dose-logs/" + id + "/replay", "").andExpect(status().isAccepted());
        verify(push).toPhone("phone-1", Map.of("type", "RING", "scheduleId", scheduleId,
                "date", dose.toLocalDate().toString()));
    }

    @Test
    void replayWithNoReachablePhoneSaysSo() throws Exception {
        String editor = api.newEditorWithFamily("Reddy family");
        String thatha = api.newPerson(editor, "Thatha", "te");
        ZonedDateTime dose = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).plusMinutes(10).withSecond(0).withNano(0);
        api.newMedicine(editor, thatha, dose.format(DateTimeFormatter.ofPattern("HH:mm")));
        doses.markMissed(dose.plusMinutes(33).toInstant());
        String id = api.read(api.get(editor, "/api/dose-logs/alerts")).get(0).get("id").asText();
        api.post(editor, "/api/dose-logs/" + id + "/replay", "").andExpect(status().isConflict());
        verify(push, org.mockito.Mockito.never()).toPhone(anyString(), any());
    }
}
