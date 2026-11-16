import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { deviceStore } from '../device/deviceStore';
import { MedAlarm, isNativeApp, type DoseLog, type PhoneStatus, type ScheduledAlarm } from '../native/medAlarm';
import { alarmChips, alarmConfig, testAlarm } from '../native/alarmConfig';
import { spokenSentence } from '../voice/sentence';

/**
 * Debug screen for testing on a real phone (English only, for the family member doing the setup):
 * fire test alarms, schedule a daily sample alarm at a chosen time, and see what is planned and logged.
 */
export function ElderDebugPage() {
  const person = deviceStore.get()?.person ?? { id: '', name: 'Thatha', voiceLanguage: 'te', timeZone: 'Asia/Kolkata' };
  const native = isNativeApp();
  const [status, setStatus] = useState<PhoneStatus | null>(null);
  const [scheduled, setScheduled] = useState<ScheduledAlarm[]>([]);
  const [logs, setLogs] = useState<DoseLog[]>([]);
  const [time, setTime] = useState(() => {
    const d = new Date(Date.now() + 3 * 60_000);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  });
  const [message, setMessage] = useState('');

  const refresh = useCallback(async () => {
    if (!native) return;
    setStatus(await MedAlarm.getStatus());
    setScheduled((await MedAlarm.getScheduled()).alarms);
    setLogs((await MedAlarm.getPendingLogs()).logs);
  }, [native]);

  useEffect(() => {
    void refresh();
    if (!native) return;
    const sub = MedAlarm.addListener('doseLogged', () => void refresh());
    const timer = setInterval(() => void refresh(), 5_000);
    return () => {
      clearInterval(timer);
      void sub.then((s) => s.remove());
    };
  }, [native, refresh]);

  async function fire(kind: 'MEDICINE' | 'ROUTINE') {
    await MedAlarm.testAlarm({ seconds: 10, repeatEveryMin: 1, stopAfterMin: 3, ...testAlarm(person, kind) });
    setMessage(`${kind === 'ROUTINE' ? 'Routine' : 'Medicine'} test alarm in 10 seconds (repeats every minute, stops after 3). Lock the phone now.`);
    void refresh();
  }

  /** A real daily alarm with the full rules (first ring 1 min before, repeat 2 min, stop 6 min after). */
  async function scheduleSample() {
    const lang = person.voiceLanguage;
    await MedAlarm.setSchedule(alarmConfig(person, [{
      id: 'sample-daily',
      kind: 'MEDICINE',
      time,
      days: [1, 2, 3, 4, 5, 6, 7],
      remindBeforeMin: 1,
      repeatEveryMin: 2,
      stopAfterMin: 6,
      title: 'బీపీ మాత్ర',
      subtitle: 'Amlodipine 5 mg · sample',
      chips: alarmChips(lang, 'BEFORE', 'ROUND', 'WHITE'),
      spoken: spokenSentence(lang, { person: person.name, voiceName: 'బీపీ మాత్ర', food: 'BEFORE' }),
      shape: 'ROUND',
      color: 'WHITE',
    }]));
    setMessage(`Daily sample alarm set for ${time}. It rings 1 minute before, every 2 minutes, and is marked missed 6 minutes after. Try rebooting the phone before it rings.`);
    void refresh();
  }

  async function clearAll() {
    await MedAlarm.cancelAll();
    setMessage('All alarms cancelled.');
    void refresh();
  }

  if (!native) {
    return <main className="p-6">This screen works in the Android app only. <Link className="underline" to="/elder">Back</Link></main>;
  }

  const fmt = (ms: number | null) => (ms ? new Date(ms).toLocaleString() : '—');
  return (
    <main lang="en" className="mx-auto flex min-h-dvh max-w-md flex-col gap-4 px-4 py-6 text-base">
      <Link to="/elder/setup" className="flex min-h-11 items-center font-bold text-teal">← Phone setup</Link>
      <h1 className="text-2xl font-bold">Alarm test and details</h1>

      <button type="button" onClick={() => fire('MEDICINE')} className="min-h-14 rounded-2xl bg-teal text-lg font-bold text-white">
        Medicine test alarm in 10 seconds
      </button>
      <button type="button" onClick={() => fire('ROUTINE')} className="min-h-14 rounded-2xl border-2 border-teal text-lg font-bold text-teal">
        Routine (calm) test alarm in 10 seconds
      </button>

      <div className="flex items-end gap-2 rounded-2xl bg-white p-3">
        <label className="flex flex-1 flex-col gap-1 font-medium">
          Daily sample alarm at
          <input type="time" value={time} onChange={(e) => setTime(e.target.value)}
            className="min-h-12 rounded-xl border border-[#C9D3D2] px-3 text-lg" />
        </label>
        <button type="button" onClick={scheduleSample} className="min-h-12 rounded-xl bg-teal-dark px-4 font-semibold text-white">
          Set
        </button>
      </div>
      {message && <p role="status" className="rounded-xl bg-[#DCEFEC] p-3">{message}</p>}

      <section className="flex flex-col gap-1 rounded-2xl bg-white p-3">
        <h2 className="font-bold">Planned</h2>
        {scheduled.length === 0 && <p className="text-muted">Nothing planned.</p>}
        {scheduled.map((a) => (
          <p key={a.id}>{a.test ? '[test] ' : ''}{a.title}: {a.type ?? 'done'} at {fmt(a.next)}</p>
        ))}
      </section>

      <section className="flex flex-col gap-1 rounded-2xl bg-white p-3">
        <h2 className="font-bold">Waiting to upload ({logs.length})</h2>
        {logs.map((l) => <p key={l.id}>{l.status} · {l.date} · {l.scheduleId} · {fmt(l.at)}</p>)}
        <p className="text-sm text-muted">Test and routine alarms are never logged. Medicine logs upload automatically when the phone is online.</p>
      </section>

      {status && (
        <section className="rounded-2xl bg-white p-3">
          <h2 className="font-bold">Phone</h2>
          <pre className="text-sm whitespace-pre-wrap">{JSON.stringify(status, null, 2)}</pre>
        </section>
      )}

      <button type="button" onClick={clearAll} className="min-h-12 rounded-xl border-2 border-danger font-semibold text-danger">
        Cancel all alarms on this phone
      </button>
    </main>
  );
}
