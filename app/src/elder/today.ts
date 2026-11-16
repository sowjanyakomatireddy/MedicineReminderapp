import type { Food, PillColour, Shape } from '../api/medicines';
import type { SyncData } from '../native/medAlarm';

export type ElderStatus = 'TAKEN' | 'MISSED' | null;

export interface ElderDose {
  key: string;
  scheduleId: string;
  time: string;
  title: string;
  food: Food;
  shape?: Shape;
  color?: PillColour;
  status: ElderStatus;
  /** The first dose still to come (or ringing now): shown large with "next". */
  next: boolean;
}

const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** Date ("2026-11-16"), weekday and minutes since midnight, now, in the person's time zone. */
export function localNow(timeZone: string, now = new Date()) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      weekday: 'long',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(now)
      .map((p) => [p.type, p.value]),
  );
  return {
    date: `${parts.year}-${parts.month}-${parts.day}`,
    weekday: parts.weekday.toUpperCase(),
    minutes: Number(parts.hour) * 60 + Number(parts.minute),
  };
}

const toMinutes = (hhmm: string) => {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
};

/**
 * Today's doses for the elder's Today screen, from the schedule stored on the phone.
 * Status comes from the phone's own answers first (they may not be uploaded yet), then the server's.
 */
export function todaysDoses(sync: SyncData, statuses: Record<string, string> = {}, now = new Date()): ElderDose[] {
  const { date, weekday, minutes } = localNow(sync.person.timeZone, now);
  if (!WEEKDAYS.includes(weekday)) return [];
  const server = Object.fromEntries(sync.logs.filter((l) => l.date === date).map((l) => [l.scheduleId, l.status]));
  const doses = sync.medicines
    .flatMap((m) =>
      m.schedules
        .filter((s) => s.active && s.id && (s.days as string[]).includes(weekday))
        .map((s) => {
          const key = `${s.id}@${date}`;
          const status = (statuses[key] ?? server[s.id!] ?? null) as ElderStatus;
          return {
            key,
            scheduleId: s.id!,
            time: s.time,
            title: m.voiceName || m.name,
            food: s.food,
            shape: m.shape,
            color: m.color,
            status,
            next: false,
            end: toMinutes(s.time) + s.stopAfterMin,
          };
        }),
    )
    .sort((a, b) => a.time.localeCompare(b.time));
  const next = doses.find((d) => d.status === null && d.end > minutes);
  return doses.map(({ end: _end, ...d }) => ({ ...d, next: next !== undefined && d.key === next.key }));
}

/** Which part of the day a time is in, for the words ఉదయం / మధ్యాహ్నం / సాయంత్రం / రాత్రి. */
export function periodKey(hhmm: string): 'morning' | 'afternoon' | 'evening' | 'night' {
  const h = Number(hhmm.split(':')[0]);
  if (h >= 5 && h < 12) return 'morning';
  if (h >= 12 && h < 17) return 'afternoon';
  if (h >= 17 && h < 21) return 'evening';
  return 'night';
}
