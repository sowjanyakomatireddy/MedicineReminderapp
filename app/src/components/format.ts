import type { Day } from '../api/medicines';

/** "07:00" → "7:00 AM" (or the local style for the language). */
export function formatTime(hhmm: string, lang?: string): string {
  const [h, m] = hhmm.split(':').map(Number);
  return new Date(2000, 0, 1, h, m).toLocaleTimeString(lang, { hour: 'numeric', minute: '2-digit' });
}

/** 1 January 2024 was a Monday, so day i of DAYS is 2024-01-(1+i). */
export function dayName(day: Day, lang: string | undefined, style: 'short' | 'long' = 'short'): string {
  const order: Day[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
  return new Date(2024, 0, 1 + order.indexOf(day)).toLocaleDateString(lang, { weekday: style });
}
