package app.medreminder.push;

import app.medreminder.config.AppProperties;
import app.medreminder.device.Device;
import app.medreminder.device.DeviceRepository;
import app.medreminder.dose.DoseLog;
import app.medreminder.person.Person;
import app.medreminder.sync.ScheduleChanged;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Who gets which push:
 * - the person's phone(s): SYNC after any change to their medicines, RING to replay a reminder;
 * - every family member: an alert when a dose is missed, in their own app language.
 */
@Component
public class Notifier {
    private final PushSender push;
    private final DeviceRepository devices;
    private final UserPushTokenRepository userTokens;
    private final MessageSource messages;
    private final String appUrl;

    public Notifier(PushSender push, DeviceRepository devices, UserPushTokenRepository userTokens,
                    MessageSource messages, AppProperties props) {
        this.push = push;
        this.devices = devices;
        this.userTokens = userTokens;
        this.messages = messages;
        this.appUrl = props.push().appUrl().replaceAll("/+$", "");
    }

    public boolean enabled() {
        return push.enabled();
    }

    /** After the change is saved, tell the person's phone to fetch the new schedule. */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onScheduleChanged(ScheduleChanged e) {
        toPhones(e.personId(), Map.of("type", "SYNC"));
    }

    /**
     * Make the person's phone ring this dose's reminder now (Taken then counts for that dose's date).
     * Returns how many phones were reached.
     */
    public int ring(UUID personId, UUID scheduleId, java.time.LocalDate date) {
        return toPhones(personId, Map.of("type", "RING", "scheduleId", scheduleId.toString(), "date", date.toString()));
    }

    public int toPhones(UUID personId, Map<String, String> data) {
        if (!push.enabled()) {
            return 0;
        }
        int sent = 0;
        List<Device> phones = devices.findByPersonIdAndRevokedAtIsNullAndFcmTokenIsNotNull(personId);
        for (Device d : phones) {
            PushSender.Result r = push.toPhone(d.getFcmToken(), data);
            if (r == PushSender.Result.SENT) {
                sent++;
            } else if (r == PushSender.Result.INVALID_TOKEN) {
                d.setFcmToken(null);
                devices.save(d);
            }
        }
        return sent;
    }

    /** "Thatha has not taken a medicine" to everyone in the family, each in their own language. */
    @Async
    public void missed(DoseLog log, Person person) {
        if (!push.enabled()) {
            return;
        }
        for (UserPushTokenRepository.Target t : userTokens.targetsInFamily(log.getFamilyId())) {
            Locale locale = Locale.forLanguageTag(t.language() == null ? "en" : t.language());
            Object[] args = {person.getName(), log.getMedicineName(), time(log.getDoseTime(), locale)};
            String title = messages.getMessage("push.missed.title", args, locale);
            String body = messages.getMessage("push.missed.body", args, locale);
            PushSender.Result r = push.toFamily(t.token(), title, body, appUrl + "/missed/" + log.getId(),
                    Map.of("type", "MISSED", "doseLogId", log.getId().toString()));
            if (r == PushSender.Result.INVALID_TOKEN) {
                userTokens.deleteByToken(t.token());
            }
        }
    }

    private static String time(LocalTime t, Locale locale) {
        return t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale));
    }
}
