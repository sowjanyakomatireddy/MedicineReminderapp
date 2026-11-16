import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorKey } from '../api/client';
import { useFamily, usePeople, type Person } from '../api/family';
import { DAYS, type Day } from '../api/medicines';
import { routineKeys, useRoutine, type RoutineInput, type RoutineItem } from '../api/routine';
import { findLocale } from '../i18n/locales';
import { routineSentence } from '../voice/sentence';
import { Alert, Button } from '../components/ui';
import { PersonChips } from '../components/PersonChips';
import { BackLink, FamilyPage, Loading } from '../components/layout';
import { dayName, formatTime } from '../components/format';
import { PeriodCard } from './PeriodCard';

/** Daily routine (prototype "Daily routine"): calm spoken reminders per person, and period dates if turned on. */
export function RoutinePage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const family = useFamily();
  const people = usePeople();
  const personId = params.get('person') ?? people.data?.[0]?.id;
  const person = people.data?.find((p) => p.id === personId);
  const isEditor = family.data?.myRole === 'EDITOR';

  if (people.isPending || family.isPending) return <Loading />;
  if (people.isError) return <FamilyPage><Alert>{t('errors.loadFailed')}</Alert></FamilyPage>;

  return (
    <FamilyPage>
      <BackLink to="/">{t('nav.home')}</BackLink>
      <h1 className="text-2xl font-bold">{t('routine.title')}</h1>
      <PersonChips people={people.data} selected={personId} canAdd={false}
        onSelect={(id) => setParams({ person: id }, { replace: true })} />
      {!person && <p className="rounded-2xl bg-white p-4">{t('home.noPeople')}</p>}
      {person?.periodTracking && <PeriodCard person={person} canEdit={isEditor} />}
      {person && <RoutineList key={person.id} person={person} canEdit={isEditor} />}
      <p className="text-[13px] leading-relaxed text-muted">{t('routine.footer')}</p>
    </FamilyPage>
  );
}

function RoutineList({ person, canEdit }: { person: Person; canEdit: boolean }) {
  const { t, i18n } = useTranslation();
  const routine = useRoutine(person.id);
  const [editing, setEditing] = useState<RoutineItem | 'new' | null>(null);
  const lang = findLocale(person.voiceLanguage)?.code ?? 'te';
  const example = routineSentence(lang, person.name, i18n.getFixedT(lang)('routine.exampleText'));

  return (
    <section aria-labelledby="routine-heading" className="flex flex-col gap-2.5">
      <div className="flex items-baseline justify-between">
        <h2 id="routine-heading" className="text-lg font-bold">{t('routine.personDay', { name: person.name })}</h2>
        {canEdit && editing === null && (
          <button type="button" onClick={() => setEditing('new')} className="min-h-11 px-1 text-sm font-semibold text-teal">
            {t('routine.add')}
          </button>
        )}
      </div>

      {editing !== null && (
        <RoutineForm person={person} item={editing === 'new' ? undefined : editing} onDone={() => setEditing(null)} />
      )}

      {routine.isPending && <Loading />}
      {routine.data?.length === 0 && editing === null && (
        <p className="rounded-[18px] bg-white p-4 text-muted">{t('routine.empty')}</p>
      )}
      {!!routine.data?.length && (
        <ul className="flex flex-col rounded-[18px] bg-white px-4 py-1">
          {routine.data.map((r, i) => (
            <li key={r.id} className={`flex items-center gap-3 py-3 ${i < routine.data.length - 1 ? 'border-b border-[#E4E9E8]' : ''}`}>
              <span className="w-[74px] flex-none text-[15px] font-bold">{formatTime(r.time, i18n.resolvedLanguage)}</span>
              <span className="flex min-w-0 flex-1 flex-col">
                <span className={r.active ? '' : 'text-muted line-through'}>{r.label}</span>
                {r.spokenText && <span lang={lang} className="text-sm text-muted">“{r.spokenText}”</span>}
                {r.days.length < 7 && (
                  <span className="text-xs text-muted">{r.days.map((d) => dayName(d, i18n.resolvedLanguage)).join(', ')}</span>
                )}
                {!r.active && <span className="text-xs font-semibold text-muted">{t('medicines.paused')}</span>}
              </span>
              {canEdit && (
                <button type="button" onClick={() => setEditing(r)} className="min-h-11 text-sm font-semibold text-teal underline"
                  aria-label={t('routine.editNamed', { name: r.label })}>
                  {t('common.edit')}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <p className="flex items-center gap-2 rounded-xl bg-[#DCEFEC] px-3 py-2.5 text-sm leading-relaxed text-teal-dark">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="flex-none">
          <path d="M4 9v6h4l5 4V5L8 9H4z" /><path d="M16.5 8.5a5 5 0 0 1 0 7" />
        </svg>
        <span>
          {t('routine.voiceNote', { language: findLocale(lang)?.englishName ?? lang })} <q lang={lang}>{example}</q>
        </span>
      </p>
    </section>
  );
}

function RoutineForm({ person, item, onDone }: { person: Person; item?: RoutineItem; onDone: () => void }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const lang = findLocale(person.voiceLanguage)?.code ?? 'te';
  const [form, setForm] = useState<RoutineInput>({
    time: item?.time ?? '07:00',
    days: item?.days ?? [...DAYS],
    label: item?.label ?? '',
    spokenText: item?.spokenText ?? '',
    active: item?.active ?? true,
  });
  const everyDay = form.days.length === 7;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : undefined;
  const preview = routineSentence(lang, person.name, form.spokenText?.trim() || form.label.trim() || '…');

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const body = JSON.stringify({ ...form, days: DAYS.filter((d) => form.days.includes(d)) });
      if (item) await api(`/api/routine/${item.id}`, { method: 'PUT', body });
      else await api(`/api/people/${person.id}/routine`, { method: 'POST', body });
      await qc.invalidateQueries({ queryKey: routineKeys.list(person.id) });
      onDone();
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  async function remove() {
    if (!item || !window.confirm(t('routine.deleteConfirm', { name: item.label }))) return;
    setBusy(true);
    try {
      await api(`/api/routine/${item.id}`, { method: 'DELETE' });
      await qc.invalidateQueries({ queryKey: routineKeys.list(person.id) });
      onDone();
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  const toggleDay = (d: Day) =>
    setForm({ ...form, days: form.days.includes(d) ? form.days.filter((x) => x !== d) : [...form.days, d] });

  return (
    <form onSubmit={save} noValidate aria-label={item ? t('routine.editTitle') : t('routine.addTitle')}
      className="flex flex-col gap-3 rounded-[18px] border-2 border-teal bg-white p-4">
      <h3 className="font-bold">{item ? t('routine.editTitle') : t('routine.addTitle')}</h3>
      <fieldset disabled={busy} className="flex flex-col gap-3">
        <div className="flex gap-3">
          <label className="flex flex-1 flex-col gap-1 text-xs font-semibold tracking-[0.04em] text-muted uppercase">
            {t('medicine.time')}
            <input type="time" value={form.time} required onChange={(e) => setForm({ ...form, time: e.target.value })}
              className="min-h-12 rounded-xl border border-[#C9D3D2] px-3 text-lg font-bold text-ink normal-case" />
          </label>
          <label className="flex flex-1 flex-col gap-1 text-xs font-semibold tracking-[0.04em] text-muted uppercase">
            {t('medicine.repeat')}
            <select value={everyDay ? 'every' : 'some'}
              onChange={(e) => setForm({ ...form, days: e.target.value === 'every' ? [...DAYS] : [] })}
              className="min-h-12 rounded-xl border border-[#C9D3D2] bg-white px-3 text-base font-semibold text-ink normal-case">
              <option value="every">{t('days.everyDay')}</option>
              <option value="some">{t('days.chooseDays')}</option>
            </select>
          </label>
        </div>
        {!everyDay && (
          <fieldset>
            <legend className="sr-only">{t('days.chooseDays')}</legend>
            <div className="flex flex-wrap gap-1.5">
              {DAYS.map((d) => {
                const on = form.days.includes(d);
                return (
                  <label key={d} className={`flex size-11 cursor-pointer items-center justify-center rounded-full border-2 text-sm font-semibold ${on ? 'border-teal bg-teal text-white' : 'border-[#C9D3D2] bg-white'}`}>
                    <input type="checkbox" className="sr-only" checked={on} onChange={() => toggleDay(d)}
                      aria-label={dayName(d, i18n.resolvedLanguage, 'long')} />
                    <span aria-hidden="true">{dayName(d, i18n.resolvedLanguage).slice(0, 3)}</span>
                  </label>
                );
              })}
            </div>
            {form.days.length === 0 && <p className="mt-1 text-danger">{t('medicine.needDay')}</p>}
          </fieldset>
        )}
        <label className="flex flex-col gap-1 font-medium">
          {t('routine.label')}
          <input value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} maxLength={100} required
            placeholder={t('routine.labelExample')} aria-invalid={!!fieldErrors?.label}
            className="min-h-12 rounded-xl border border-[#C9D3D2] px-3 text-base" />
        </label>
        <label className="flex flex-col gap-1 font-medium">
          {t('routine.spoken', { language: findLocale(lang)?.nativeName ?? lang })}
          <input lang={lang} value={form.spokenText} onChange={(e) => setForm({ ...form, spokenText: e.target.value })}
            maxLength={200} placeholder={i18n.getFixedT(lang)('routine.exampleText')}
            className="min-h-12 rounded-xl border border-[#C9D3D2] px-3 text-base" />
          <span className="text-sm font-normal text-muted">{t('routine.spokenHint')}</span>
        </label>
        <p className="rounded-xl bg-[#DCEFEC] px-3 py-2 text-sm text-teal-dark" aria-live="polite">
          {t('medicine.preview')} <q lang={lang}>{preview}</q>
        </p>
        {item && (
          <label className="flex min-h-11 items-center gap-2 font-medium">
            <input type="checkbox" className="size-5 accent-teal" checked={form.active}
              onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            {t('medicine.timeActive')}
          </label>
        )}
        {error != null && <Alert>{t(fieldErrors ? 'errors.checkFields' : errorKey(error))}</Alert>}
        <div className="flex gap-2">
          <Button type="submit" disabled={!form.label.trim() || form.days.length === 0}>{t('common.save')}</Button>
          <button type="button" onClick={onDone} className="min-h-14 rounded-2xl border-2 border-teal px-5 font-semibold text-teal">
            {t('common.cancel')}
          </button>
        </div>
        {item && (
          <button type="button" onClick={remove} className="min-h-11 self-start text-sm font-semibold text-danger underline">
            {t('routine.delete')}
          </button>
        )}
      </fieldset>
    </form>
  );
}
