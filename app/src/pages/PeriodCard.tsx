import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { api, errorKey } from '../api/client';
import { keys, type Person } from '../api/family';
import { routineKeys, usePeriod, type Period, type PeriodSettings } from '../api/routine';
import { findLocale } from '../i18n/locales';
import { periodSentence } from '../voice/sentence';
import { Alert } from '../components/ui';

/** "2026-11-20" as a local date (no time-zone shift). */
const parse = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};
const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/**
 * Period card (prototype, shown only when turned on for the person): next expected date, a 7-day strip,
 * what the voice will say and when, "Period started today", and settings. Always marked as an estimate.
 */
export function PeriodCard({ person, canEdit }: { person: Person; canEdit: boolean }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const period = usePeriod(person.id);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const lang = findLocale(person.voiceLanguage)?.code ?? 'te';

  async function call(path: string, init: RequestInit) {
    setBusy(true);
    setError(null);
    try {
      const updated = await api<Period>(path, init);
      qc.setQueryData(routineKeys.period(person.id), updated);
      if (!updated.enabled) await qc.invalidateQueries({ queryKey: keys.people });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  const recordStart = (date?: string) =>
    call(`/api/people/${person.id}/period/starts`, { method: 'POST', body: JSON.stringify(date ? { date } : {}) });

  if (period.isPending) return null;
  if (period.isError || !period.data) return <Alert>{t('errors.loadFailed')}</Alert>;
  const p = period.data;
  const locale = i18n.resolvedLanguage;
  const fmt = (d: string, opts: Intl.DateTimeFormatOptions) => parse(d).toLocaleDateString(locale, opts);

  return (
    <section aria-labelledby="period-heading" className="flex flex-col gap-3 rounded-3xl bg-[#FBE6EA] p-[18px]">
      <div className="flex flex-col gap-1">
        <h2 id="period-heading" className="text-[13px] font-semibold tracking-[0.04em] text-[#7A2236] uppercase">
          {t('period.nextExpected')}
        </h2>
        {p.nextExpected && p.daysUntil !== undefined ? (
          <p className="flex flex-wrap items-baseline gap-2">
            <span className="text-[34px] leading-tight font-bold">
              {p.daysUntil > 0 ? t('period.inDays', { count: p.daysUntil })
                : p.daysUntil === 0 ? t('period.today') : t('period.daysAgo', { count: -p.daysUntil })}
            </span>
            <span className="text-base text-[#5A2431]">{fmt(p.nextExpected, { weekday: 'short', day: 'numeric', month: 'short' })}</span>
          </p>
        ) : (
          <p className="text-base text-[#5A2431]">{t('period.noDates')}</p>
        )}
      </div>

      {p.nextExpected && <Strip period={p} />}

      <p className="text-sm leading-relaxed text-[#5A2431]">
        {t('period.voiceBefore', { count: p.remindDaysBefore })}{' '}
        <q lang={lang}>{periodSentence(lang, person.name)}</q>
      </p>

      <button type="button" onClick={() => recordStart()} disabled={busy}
        className="min-h-12 rounded-2xl bg-[#A61B3B] font-bold text-white disabled:opacity-60">
        {t('period.startedToday')}
      </button>
      <PastStart onSave={recordStart} busy={busy} today={p.today} />
      {error != null && <Alert>{t(errorKey(error))}</Alert>}

      {p.recentStarts.length > 0 && (
        <details className="text-sm">
          <summary className="min-h-11 cursor-pointer py-2 font-semibold text-[#5A2431]">
            {t('period.history', { count: p.recentStarts.length })}
          </summary>
          <p className="pb-1 text-[#5A2431]">{t('period.average', { count: p.averageCycleDays })}</p>
          <ul className="flex flex-col">
            {p.recentStarts.map((d) => (
              <li key={d} className="flex items-center justify-between gap-2 border-b border-[#F5D3DA] py-1">
                <span>{fmt(d, { day: 'numeric', month: 'long', year: 'numeric' })}</span>
                <button type="button" disabled={busy}
                  onClick={() => window.confirm(t('period.removeConfirm')) &&
                    call(`/api/people/${person.id}/period/starts/${d}`, { method: 'DELETE' })}
                  className="min-h-11 px-2 font-semibold text-danger underline">
                  {t('period.remove')}
                </button>
              </li>
            ))}
          </ul>
        </details>
      )}

      {canEdit && <Settings period={p} onSave={(s) => call(`/api/people/${person.id}/period`, { method: 'PUT', body: JSON.stringify(s) })} busy={busy} />}

      <p className="text-[13px] font-semibold text-[#5A2431]">{t('period.disclaimer')}</p>
    </section>
  );
}

/** Seven days from today: today outlined dark, the expected start red, the expected days pink, each with words. */
function Strip({ period: p }: { period: Period }) {
  const { t, i18n } = useTranslation();
  const start = parse(p.nextExpected!);
  const end = new Date(start);
  end.setDate(end.getDate() + p.periodLengthDays - 1);
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = parse(p.today);
    d.setDate(d.getDate() + i);
    return d;
  });
  return (
    <ol className="flex justify-between rounded-2xl bg-white px-2 py-2.5">
      {days.map((d, i) => {
        const isStart = iso(d) === p.nextExpected;
        const inPeriod = d >= start && d <= end;
        const cls = isStart ? 'bg-[#C8364B] text-white font-bold'
          : inPeriod ? 'bg-[#F5D3DA]' : i === 0 ? 'bg-ink text-white font-bold' : '';
        const label = [
          d.toLocaleDateString(i18n.resolvedLanguage, { weekday: 'long', day: 'numeric', month: 'long' }),
          i === 0 && t('period.todayLabel'),
          isStart ? t('period.expectedStart') : inPeriod && t('period.expectedDay'),
        ].filter(Boolean).join(', ');
        return (
          <li key={i} className="flex w-9 flex-col items-center gap-1">
            <span aria-hidden="true" className="text-[11px] font-semibold text-muted">
              {d.toLocaleDateString(i18n.resolvedLanguage, { weekday: 'narrow' })}
            </span>
            <span aria-hidden="true" className={`flex size-8 items-center justify-center rounded-full text-sm font-semibold ${cls}`}>
              {d.getDate()}
            </span>
            <span className="sr-only">{label}</span>
          </li>
        );
      })}
    </ol>
  );
}

function PastStart({ onSave, busy, today }: { onSave: (d: string) => void; busy: boolean; today: string }) {
  const { t } = useTranslation();
  const [date, setDate] = useState('');
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (date) onSave(date);
  };
  return (
    <form onSubmit={submit} className="flex items-end gap-2">
      <label className="flex flex-1 flex-col gap-1 text-sm font-medium text-[#5A2431]">
        {t('period.otherDate')}
        <input type="date" value={date} max={today} onChange={(e) => setDate(e.target.value)}
          className="min-h-11 rounded-xl border border-[#E7B7C1] bg-white px-3 text-base text-ink" />
      </label>
      <button type="submit" disabled={busy || !date}
        className="min-h-11 rounded-xl border-2 border-[#A61B3B] bg-white px-4 font-semibold text-[#A61B3B] disabled:opacity-60">
        {t('common.save')}
      </button>
    </form>
  );
}

function Settings({ period: p, onSave, busy }: { period: Period; onSave: (s: PeriodSettings) => void; busy: boolean }) {
  const { t } = useTranslation();
  const [s, setS] = useState<PeriodSettings>({
    enabled: p.enabled, cycleLengthDays: p.cycleLengthDays, periodLengthDays: p.periodLengthDays,
    remindDaysBefore: p.remindDaysBefore, remindTime: p.remindTime,
  });
  const num = (key: keyof PeriodSettings, min: number, max: number, label: string) => (
    <label className="flex items-center justify-between gap-2">
      {label}
      <input type="number" min={min} max={max} value={s[key] as number}
        onChange={(e) => setS({ ...s, [key]: Number(e.target.value) })}
        className="min-h-11 w-20 rounded-lg border border-[#E7B7C1] bg-white px-2 text-right text-base" />
    </label>
  );
  return (
    <details className="text-sm">
      <summary className="min-h-11 cursor-pointer py-2 font-semibold text-[#5A2431]">{t('period.settings')}</summary>
      <div className="flex flex-col gap-2 pt-1">
        {num('cycleLengthDays', 15, 60, t('period.cycleLength'))}
        {num('periodLengthDays', 1, 10, t('period.periodLength'))}
        {num('remindDaysBefore', 0, 10, t('period.remindDays'))}
        <label className="flex items-center justify-between gap-2">
          {t('period.remindTime')}
          <input type="time" value={s.remindTime} onChange={(e) => setS({ ...s, remindTime: e.target.value })}
            className="min-h-11 rounded-lg border border-[#E7B7C1] bg-white px-2 text-base" />
        </label>
        <label className="flex min-h-11 items-center gap-2 font-medium">
          <input type="checkbox" className="size-5 accent-teal" checked={s.enabled}
            onChange={(e) => setS({ ...s, enabled: e.target.checked })} />
          {t('period.enabled')}
        </label>
        <button type="button" disabled={busy} onClick={() => onSave(s)}
          className="min-h-11 rounded-xl bg-[#A61B3B] font-semibold text-white disabled:opacity-60">
          {t('common.save')}
        </button>
      </div>
    </details>
  );
}
