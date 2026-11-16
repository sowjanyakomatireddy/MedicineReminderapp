package app.medreminder.sync;

import java.util.UUID;

/**
 * Something the person's phone needs changed (a medicine, a time, their language...).
 * After the database commit, Notifier sends the phone a SYNC push so it fetches the new schedule.
 */
public record ScheduleChanged(UUID personId) {
}
