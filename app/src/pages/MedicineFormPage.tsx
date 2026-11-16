import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorKey } from '../api/client';
import { keys, useFamily, usePeople, type Person } from '../api/family';
import {
  COLOURS,
  DAYS,
  FOODS,
  SHAPES,
  medicineKeys,
  useMedicine,
  type Day,
  type Food,
  type Medicine,
  type MedicineInput,
  type PillColour,
  type Shape,
} from '../api/medicines';
import { uploadPhoto } from '../api/photos';
import { LOCALES, findLocale } from '../i18n/locales';
import { hasBrowserVoice, speakPreview, spokenSentence } from '../voice/sentence';
import { dayName } from '../components/format';
import { Alert, Button } from '../components/ui';
import { PhotoPicker } from '../components/PhotoPicker';
import { PillIcon } from '../components/PillIcon';
import { FamilyPage, Loading } from '../components/layout';

interface TimeDraft {
  key: number;
  id?: string;
  time: string;
  everyDay: boolean;
  days: Day[];
  food: Food;
  active: boolean;
}

const REMIND_BEFORE = [0, 5, 10, 15, 30];
const REPEAT_EVERY = [2, 5, 10, 15];
const STOP_AFTER = [15, 30, 45, 60, 90];

let nextKey = 1;
const newTime = (time = '07:00', food: Food = 'BEFORE'): TimeDraft => ({
  key: nextKey++,
  time,
  everyDay: true,
  days: [...DAYS],
  food,
  active: true,
});

/** /medicines/new?person=<id> and /medicines/:id */
export function MedicineFormPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const family = useFamily();
  const people = usePeople();
  const medicine = useMedicine(id);

  if (family.isPending || people.isPending || (id && medicine.isPending)) return <Loading />;
  if (people.isError || (id && medicine.isError)) return <LoadError />;
  const list = people.data ?? [];
  if (list.length === 0) return <NoPeople />;
  const personId = medicine.data?.personId ?? params.get('person') ?? list[0].id;
  return (
    <MedicineForm
      key={medicine.data?.id ?? 'new'}
      medicine={medicine.data}
      people={list}
      initialPersonId={list.some((p) => p.id === personId) ? personId : list[0].id}
      canEdit={family.data?.myRole === 'EDITOR'}
    />
  );
}

function MedicineForm({
  medicine,
  people,
  initialPersonId,
  canEdit,
}: {
  medicine?: Medicine;
  people: Person[];
  initialPersonId: string;
  canEdit: boolean;
}) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [personId, setPersonId] = useState(initialPersonId);
  const person = people.find((p) => p.id === personId)!;

  const [name, setName] = useState(medicine?.name ?? '');
  const [voiceName, setVoiceName] = useState(medicine?.voiceName ?? '');
  const [voiceLanguage, setVoiceLanguage] = useState(person.voiceLanguage);
  const [shape, setShape] = useState<Shape | undefined>(medicine?.shape);
  const [colour, setColour] = useState<PillColour | undefined>(medicine?.color);
  const [notes, setNotes] = useState(medicine?.notes ?? '');
  const [photo, setPhoto] = useState<File | null>(null);
  const [removePhoto, setRemovePhoto] = useState(false);
  const [times, setTimes] = useState<TimeDraft[]>(() =>
    medicine
      ? medicine.schedules.map((s) => ({
          key: nextKey++,
          id: s.id,
          time: s.time,
          everyDay: s.days.length === 7,
          days: s.days,
          food: s.food,
          active: s.active,
        }))
      : [newTime()],
  );
  const first = medicine?.schedules[0];
  const [remindBefore, setRemindBefore] = useState(first?.remindBeforeMin ?? 5);
  const [repeatEvery, setRepeatEvery] = useState(first?.repeatEveryMin ?? 5);
  const [stopAfter, setStopAfter] = useState(first?.stopAfterMin ?? 30);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  // Switching person (new medicine only) shows that person's voice language.
  useEffect(() => setVoiceLanguage(person.voiceLanguage), [person.voiceLanguage]);

  const fieldErrors = error instanceof ApiError ? error.fieldErrors : undefined;
  const preview = spokenSentence(voiceLanguage, {
    person: person.name,
    voiceName: voiceName.trim() || name.trim() || t('medicine.previewPlaceholder'),
    food: times[0]?.food ?? 'ANY',
  });
  const voiceAvailable = useMemo(() => hasBrowserVoice(voiceLanguage), [voiceLanguage]);
  const missingDays = times.some((x) => !x.everyDay && x.days.length === 0);
  const canSave = canEdit && name.trim() && times.length > 0 && !missingDays && !busy;

  const updateTime = (key: number, patch: Partial<TimeDraft>) =>
    setTimes((ts) => ts.map((x) => (x.key === key ? { ...x, ...patch } : x)));

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!canSave) return;
    setBusy(true);
    setError(null);
    const body: MedicineInput = {
      personId,
      name,
      voiceName,
      shape,
      color: colour,
      notes,
      schedules: times.map((x) => ({
        id: x.id,
        time: x.time,
        days: x.everyDay ? [...DAYS] : DAYS.filter((d) => x.days.includes(d)),
        food: x.food,
        remindBeforeMin: remindBefore,
        repeatEveryMin: repeatEvery,
        stopAfterMin: stopAfter,
        active: x.active,
      })),
    };
    try {
      // The voice language belongs to the person, so changing it here changes it for all their reminders.
      if (voiceLanguage !== person.voiceLanguage) {
        await api(`/api/people/${person.id}`, {
          method: 'PUT',
          body: JSON.stringify({
            name: person.name, voiceLanguage, timeZone: person.timeZone, phone: person.phone, familyPhone: person.familyPhone,
          }),
        });
      }
      const saved = medicine
        ? await api<Medicine>(`/api/medicines/${medicine.id}`, { method: 'PUT', body: JSON.stringify(body) })
        : await api<Medicine>('/api/medicines', { method: 'POST', body: JSON.stringify(body) });
      if (photo) await uploadPhoto(`/api/medicines/${saved.id}/photo`, photo);
      else if (removePhoto) await api(`/api/medicines/${saved.id}/photo`, { method: 'DELETE' });
      await Promise.all([
        qc.invalidateQueries({ queryKey: medicineKeys.all }),
        qc.invalidateQueries({ queryKey: medicineKeys.today }),
        qc.invalidateQueries({ queryKey: keys.people }),
      ]);
      navigate(`/?person=${personId}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  async function remove() {
    if (!medicine || !window.confirm(t('medicine.deleteConfirm', { name: medicine.name }))) return;
    setBusy(true);
    try {
      await api(`/api/medicines/${medicine.id}`, { method: 'DELETE' });
      await Promise.all([
        qc.invalidateQueries({ queryKey: medicineKeys.all }),
        qc.invalidateQueries({ queryKey: medicineKeys.today }),
        qc.invalidateQueries({ queryKey: keys.people }),
      ]);
      navigate(`/medicines?person=${personId}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <FamilyPage nav={false}>
      <div className="flex items-center justify-between gap-2">
        <Link to={medicine ? `/medicines?person=${personId}` : `/?person=${personId}`}
          className="flex min-h-11 items-center font-bold text-teal">
          <span aria-hidden="true">←&nbsp;</span>
          {t('common.back')}
        </Link>
        <h1 className="text-[22px] font-bold">{medicine ? t('medicine.editTitle') : t('medicine.addTitle')}</h1>
        <span className="min-w-[60px] text-right text-sm font-semibold text-muted">
          {t('medicine.forPerson', { name: person.name })}
        </span>
      </div>
      {!canEdit && <Alert>{t('person.viewerNote')}</Alert>}

      <form onSubmit={save} noValidate className="flex flex-col gap-4">
        <fieldset disabled={!canEdit || busy} className="flex flex-col gap-4">
          {!medicine && people.length > 1 && (
            <Field label={t('medicine.forWho')} htmlFor="for-who">
              <select id="for-who" value={personId} onChange={(e) => setPersonId(e.target.value)}
                className="min-h-12 rounded-xl border border-[#C9D3D2] bg-white px-3 text-base">
                {people.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </Field>
          )}

          <div className="flex items-stretch gap-3">
            <PhotoPicker
              className="w-24 flex-none"
              label={t('medicine.photo')}
              existingPath={medicine?.hasPhoto && !removePhoto ? `/api/medicines/${medicine.id}/photo` : undefined}
              existingVersion={medicine?.photoVersion}
              file={photo}
              onFile={(f) => { setPhoto(f); setRemovePhoto(false); }}
              onRemove={() => setRemovePhoto(true)}
            />
            <div className="flex flex-1 flex-col gap-2">
              <Field label={t('medicine.name')} htmlFor="med-name" error={fieldErrors?.name}>
                <input id="med-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required
                  aria-invalid={!!fieldErrors?.name}
                  className="min-h-12 rounded-xl border border-[#C9D3D2] bg-white px-3 text-base" />
              </Field>
              <Field label={t('medicine.voiceName')} htmlFor="voice-name">
                <input id="voice-name" lang={voiceLanguage} value={voiceName} onChange={(e) => setVoiceName(e.target.value)}
                  maxLength={100} placeholder={name || undefined}
                  className="min-h-12 rounded-xl border border-[#C9D3D2] bg-white px-3 text-base" />
              </Field>
            </div>
          </div>

          <fieldset className="flex flex-col gap-1.5">
            <Legend>{t('medicine.voiceLanguage')}</Legend>
            <div className="flex flex-wrap gap-2">
              {LOCALES.map((l) => (
                <ChipRadio key={l.code} name="voice-language" checked={voiceLanguage === l.code}
                  onChange={() => setVoiceLanguage(l.code)}>
                  <span lang={l.code}>{l.nativeName}</span>
                </ChipRadio>
              ))}
            </div>
            {voiceLanguage !== person.voiceLanguage && (
              <p className="text-sm text-muted">{t('medicine.voiceLanguageNote', { name: person.name })}</p>
            )}
            <div className="flex items-center gap-2 rounded-xl bg-[#DCEFEC] px-3 py-2 text-sm text-teal-dark">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="flex-none">
                <path d="M4 9v6h4l5 4V5L8 9H4z" /><path d="M16.5 8.5a5 5 0 0 1 0 7" />
              </svg>
              <p className="flex-1" aria-live="polite">
                {t('medicine.preview')} <q lang={voiceLanguage}>{preview}</q>
              </p>
              {voiceAvailable && (
                <button type="button" onClick={() => speakPreview(voiceLanguage, preview)}
                  className="min-h-11 rounded-lg px-2 font-semibold underline">
                  {t('medicine.listen')}
                </button>
              )}
            </div>
            {voiceAvailable === false && (
              <p className="text-sm text-muted">
                {t('medicine.noVoice', { language: findLocale(voiceLanguage)?.englishName ?? voiceLanguage })}
              </p>
            )}
          </fieldset>

          {times.map((x, i) => (
            <fieldset key={x.key} className="flex flex-col gap-3 rounded-2xl border border-[#DCE3E2] p-3">
              <legend className="px-1 text-xs font-semibold tracking-[0.04em] text-muted uppercase">
                {times.length > 1 ? t('medicine.timeN', { n: i + 1 }) : t('medicine.time')}
              </legend>
              <div className="flex gap-3">
                <Field label={t('medicine.time')} htmlFor={`time-${x.key}`} className="flex-1">
                  <input id={`time-${x.key}`} type="time" value={x.time} required
                    onChange={(e) => updateTime(x.key, { time: e.target.value })}
                    className="min-h-14 rounded-xl border border-[#C9D3D2] bg-white px-3 text-xl font-bold" />
                </Field>
                <Field label={t('medicine.repeat')} htmlFor={`repeat-${x.key}`} className="flex-1">
                  <select id={`repeat-${x.key}`} value={x.everyDay ? 'every' : 'some'}
                    onChange={(e) => {
                      const every = e.target.value === 'every';
                      // "Chosen days" starts empty, so the family taps the days they want.
                      updateTime(x.key, every ? { everyDay: true } : { everyDay: false, days: x.days.length === 7 ? [] : x.days });
                    }}
                    className="min-h-14 rounded-xl border border-[#C9D3D2] bg-white px-3 text-lg font-semibold">
                    <option value="every">{t('days.everyDay')}</option>
                    <option value="some">{t('days.chooseDays')}</option>
                  </select>
                </Field>
              </div>
              {!x.everyDay && (
                <fieldset>
                  <legend className="sr-only">{t('days.chooseDays')}</legend>
                  <div className="flex flex-wrap gap-1.5">
                    {DAYS.map((d) => {
                      const on = x.days.includes(d);
                      return (
                        <label key={d} className={`flex size-11 cursor-pointer items-center justify-center rounded-full border-2 text-sm font-semibold ${on ? 'border-teal bg-teal text-white' : 'border-[#C9D3D2] bg-white'}`}>
                          <input type="checkbox" className="sr-only" checked={on}
                            aria-label={dayName(d, i18n.resolvedLanguage, 'long')}
                            onChange={() => updateTime(x.key, { days: on ? x.days.filter((y) => y !== d) : [...x.days, d] })} />
                          <span aria-hidden="true">{dayName(d, i18n.resolvedLanguage, 'short').slice(0, 3)}</span>
                        </label>
                      );
                    })}
                  </div>
                  {x.days.length === 0 && <p className="mt-1 text-danger">{t('medicine.needDay')}</p>}
                </fieldset>
              )}
              <fieldset className="flex flex-col gap-1.5">
                <Legend>{t('medicine.withFood')}</Legend>
                <div className="flex gap-1 rounded-[14px] bg-[#E4E9E8] p-1">
                  {FOODS.map((f) => {
                    const on = x.food === f;
                    return (
                      <label key={f} className={`flex min-h-11 flex-1 cursor-pointer items-center justify-center rounded-[10px] px-2 text-center text-[15px] focus-within:outline focus-within:outline-3 focus-within:outline-teal ${on ? 'bg-white font-bold text-before-fg' : 'font-semibold text-muted'}`}>
                        <input type="radio" name={`food-${x.key}`} className="sr-only" checked={on}
                          onChange={() => updateTime(x.key, { food: f })} />
                        {on && <span aria-hidden="true">✓&nbsp;</span>}
                        {t(`food.${f}`)}
                      </label>
                    );
                  })}
                </div>
              </fieldset>
              {(medicine || times.length > 1) && (
                <div className="flex items-center justify-between gap-2">
                  {medicine && x.id ? (
                    <label className="flex min-h-11 items-center gap-2 font-medium">
                      <input type="checkbox" className="size-5 accent-teal" checked={x.active}
                        onChange={(e) => updateTime(x.key, { active: e.target.checked })} />
                      {t('medicine.timeActive')}
                    </label>
                  ) : <span />}
                  {times.length > 1 && (
                    <button type="button" onClick={() => setTimes((ts) => ts.filter((y) => y.key !== x.key))}
                      className="min-h-11 text-sm font-semibold text-danger underline">
                      {t('medicine.removeTime')}
                    </button>
                  )}
                </div>
              )}
            </fieldset>
          ))}
          {times.length < 12 && (
            <button type="button" onClick={() => setTimes((ts) => [...ts, newTime('21:00', 'AFTER')])}
              className="min-h-12 rounded-2xl border-2 border-dashed border-teal font-bold text-teal">
              {t('medicine.addTime')}
            </button>
          )}

          <fieldset className="flex flex-col gap-1.5">
            <Legend>{t('medicine.alarm')}</Legend>
            <div className="flex flex-col rounded-[14px] bg-white px-3.5 py-1">
              <AlarmRow id="remind-before" label={t('medicine.firstReminder')} value={remindBefore} onChange={setRemindBefore}
                options={REMIND_BEFORE.map((m) => [m, m === 0 ? t('medicine.atTheTime') : t('medicine.minBefore', { count: m })])} />
              <AlarmRow id="repeat-every" label={t('medicine.repeatEvery')} value={repeatEvery} onChange={setRepeatEvery}
                options={REPEAT_EVERY.map((m) => [m, t('medicine.minutes', { count: m })])} />
              <AlarmRow id="stop-after" label={t('medicine.stopAfter')} value={stopAfter} onChange={setStopAfter} last
                options={STOP_AFTER.map((m) => [m, t('medicine.minutes', { count: m })])} />
            </div>
          </fieldset>

          <fieldset className="flex flex-col gap-1.5">
            <Legend>{t('medicine.looksLike')}</Legend>
            <div className="flex items-start gap-3">
              <PillIcon shape={shape} color={colour} size={52} />
              <div className="flex flex-1 flex-col gap-2">
                <div role="radiogroup" aria-label={t('medicine.shape')} className="flex flex-wrap gap-1.5">
                  {SHAPES.map((s) => (
                    <ChipRadio key={s} small name="shape" checked={shape === s} onChange={() => setShape(shape === s ? undefined : s)}>
                      {t(`shape.${s}`)}
                    </ChipRadio>
                  ))}
                </div>
                <div role="radiogroup" aria-label={t('medicine.colour')} className="flex flex-wrap gap-1.5">
                  {COLOURS.map((c) => (
                    <ChipRadio key={c} small name="colour" checked={colour === c} onChange={() => setColour(colour === c ? undefined : c)}>
                      {t(`colour.${c}`)}
                    </ChipRadio>
                  ))}
                </div>
              </div>
            </div>
          </fieldset>

          <Field label={t('medicine.notes')} htmlFor="notes" hint={t('medicine.notesHint')}>
            <textarea id="notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} rows={2}
              className="rounded-xl border border-[#C9D3D2] bg-white px-3 py-2 text-base" />
          </Field>

          <p className="text-sm text-muted">{t('common.disclaimer')}</p>
          {error != null && !fieldErrors && <Alert>{t(errorKey(error))}</Alert>}
          {fieldErrors && <Alert>{t('errors.checkFields')}</Alert>}
          {canEdit && (
            <Button type="submit" disabled={!canSave}>
              {t('medicine.save', { name: person.name })}
            </Button>
          )}
        </fieldset>
      </form>

      {medicine && canEdit && (
        <button type="button" onClick={remove} disabled={busy}
          className="min-h-14 rounded-2xl border-2 border-danger font-semibold text-danger">
          {t('medicine.delete')}
        </button>
      )}
    </FamilyPage>
  );
}

function Field({ label, htmlFor, hint, error, className = '', children }: {
  label: string; htmlFor: string; hint?: string; error?: string; className?: string; children: ReactNode;
}) {
  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label htmlFor={htmlFor} className="text-xs font-semibold tracking-[0.04em] text-muted uppercase">{label}</label>
      {children}
      {(error || hint) && <p className={`text-sm ${error ? 'text-danger' : 'text-muted'}`}>{error ?? hint}</p>}
    </div>
  );
}

function Legend({ children }: { children: ReactNode }) {
  return <legend className="mb-1.5 text-xs font-semibold tracking-[0.04em] text-muted uppercase">{children}</legend>;
}

function ChipRadio({ name, checked, onChange, small, children }: {
  name: string; checked: boolean; onChange: () => void; small?: boolean; children: ReactNode;
}) {
  return (
    <label className={`flex cursor-pointer items-center gap-1 rounded-full border font-semibold focus-within:outline focus-within:outline-3 focus-within:outline-teal ${small ? 'min-h-11 px-3 text-sm' : 'min-h-11 px-3.5 text-[15px]'} ${checked ? 'border-teal bg-teal text-white' : 'border-[#C9D3D2] bg-white text-ink'}`}>
      {/* A checkbox look-alike radio: tapping the chosen chip again clears an optional choice. */}
      <input type="radio" name={name} checked={checked} onChange={onChange} onClick={() => checked && onChange()} className="sr-only" />
      {checked && <span aria-hidden="true">✓</span>}
      {children}
    </label>
  );
}

function AlarmRow({ id, label, value, onChange, options, last }: {
  id: string; label: string; value: number; onChange: (v: number) => void; options: [number, string][]; last?: boolean;
}) {
  return (
    <div className={`flex items-center justify-between gap-3 py-2 ${last ? '' : 'border-b border-[#E4E9E8]'}`}>
      <label htmlFor={id} className="text-[15px]">{label}</label>
      <select id={id} value={value} onChange={(e) => onChange(Number(e.target.value))}
        className="min-h-11 rounded-lg border border-[#C9D3D2] bg-white px-2 font-bold">
        {options.map(([v, text]) => <option key={v} value={v}>{text}</option>)}
      </select>
    </div>
  );
}

function LoadError() {
  const { t } = useTranslation();
  return (
    <FamilyPage nav={false}>
      <Alert>{t('errors.loadFailed')}</Alert>
    </FamilyPage>
  );
}

function NoPeople() {
  const { t } = useTranslation();
  return (
    <FamilyPage nav={false}>
      <p className="rounded-2xl bg-white p-4">{t('home.noPeople')}</p>
      <Link to="/family/people/new" className="flex min-h-14 items-center justify-center rounded-2xl bg-teal font-semibold text-white">
        {t('home.addFirstPerson')}
      </Link>
    </FamilyPage>
  );
}
