import { describe, expect, it } from 'vitest';
import type { SyncData } from '../native/medAlarm';
import { localNow, periodKey, todaysDoses } from './today';

const every = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const;
const sched = (id: string, time: string, extra = {}) => ({
  id, time, days: [...every], food: 'BEFORE' as const, remindBeforeMin: 5, repeatEveryMin: 5, stopAfterMin: 30, active: true, ...extra,
});
const sync = (logs: SyncData['logs'] = []): SyncData => ({
  person: { id: 'p1', name: 'Thatha', voiceLanguage: 'te', timeZone: 'Asia/Kolkata', familyPhone: '+91 90000 00000' },
  familyName: 'Reddy family',
  medicines: [
    { id: 'm1', personId: 'p1', name: 'Amlodipine', voiceName: 'బీపీ మాత్ర', hasPhoto: false, updatedAt: '',
      schedules: [sched('s1', '07:00'), sched('s3', '21:00'), sched('paused', '08:00', { active: false })] },
    { id: 'm2', personId: 'p1', name: 'Sugar tablet', hasPhoto: false, updatedAt: '', schedules: [sched('s2', '13:30', { days: ['MONDAY'] })] },
  ],
  logs,
  serverTime: '',
  pushEnabled: false,
});
// Monday 17 November 2025, 10:00 in India.
const monday10 = new Date('2025-11-17T04:30:00Z');

describe('elder Today', () => {
  it('works in the person’s time zone', () => {
    expect(localNow('Asia/Kolkata', monday10)).toEqual({ date: '2025-11-17', weekday: 'MONDAY', minutes: 600 });
  });

  it('lists today’s active doses in time order and marks the next one', () => {
    const doses = todaysDoses(sync([{ scheduleId: 's1', date: '2025-11-17', status: 'TAKEN' }]), {}, monday10);
    expect(doses.map((d) => [d.time, d.title, d.status, d.next])).toEqual([
      ['07:00', 'బీపీ మాత్ర', 'TAKEN', false],
      ['13:30', 'Sugar tablet', null, true],
      ['21:00', 'బీపీ మాత్ర', null, false],
    ]);
  });

  it('the phone’s own answer wins (it may not be uploaded yet), and a missed one is not "next"', () => {
    const doses = todaysDoses(sync(), { 's1@2025-11-17': 'MISSED', 's2@2025-11-17': 'TAKEN' }, monday10);
    expect(doses.map((d) => d.status)).toEqual(['MISSED', 'TAKEN', null]);
    expect(doses.find((d) => d.next)?.time).toBe('21:00');
  });

  it('a dose still ringing counts as next', () => {
    const at0720 = new Date('2025-11-17T01:50:00Z'); // 07:20 IST, within 07:00 + 30 min
    expect(todaysDoses(sync(), {}, at0720).find((d) => d.next)?.time).toBe('07:00');
  });

  it('period words', () => {
    expect([periodKey('07:00'), periodKey('13:30'), periodKey('18:00'), periodKey('22:00')])
      .toEqual(['morning', 'afternoon', 'evening', 'night']);
  });
});
