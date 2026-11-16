import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorKey } from '../api/client';
import { keys, useFamily, usePerson, type Person, type PersonInput } from '../api/family';
import { LOCALES } from '../i18n/locales';
import { Alert, Button, TextField } from '../components/ui';
import { BackLink, FamilyPage, Loading } from '../components/layout';
import { PhotoPicker } from '../components/PhotoPicker';
import { uploadPhoto } from '../api/photos';

const DEFAULT_TZ = 'Asia/Kolkata';

function timeZones(current: string): string[] {
  let all: string[] = [];
  try {
    all = (Intl as unknown as { supportedValuesOf?: (k: string) => string[] }).supportedValuesOf?.('timeZone') ?? [];
  } catch {
    /* older browsers */
  }
  return [...new Set([DEFAULT_TZ, current, ...all])];
}

/** /family/people/new and /family/people/:id */
export function PersonFormPage() {
  const { id } = useParams();
  const person = usePerson(id);
  const family = useFamily();

  if (family.isPending || (id && person.isPending)) return <Loading />;
  return <PersonForm person={person.data} canEdit={family.data?.myRole === 'EDITOR'} />;
}

function PersonForm({ person, canEdit }: { person?: Person; canEdit: boolean }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [form, setForm] = useState<PersonInput>({
    name: person?.name ?? '',
    voiceLanguage: person?.voiceLanguage ?? 'te', // Telugu first
    timeZone: person?.timeZone ?? DEFAULT_TZ,
    phone: person?.phone ?? '',
    familyPhone: person?.familyPhone ?? '',
    periodTracking: person?.periodTracking ?? false,
  });
  const [photo, setPhoto] = useState<File | null>(null);
  const [removePhoto, setRemovePhoto] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const zones = useMemo(() => timeZones(form.timeZone), [form.timeZone]);
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : undefined;

  useEffect(() => setError(null), [form]);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const saved = person
        ? await api<Person>(`/api/people/${person.id}`, { method: 'PUT', body: JSON.stringify(form) })
        : await api<Person>('/api/people', { method: 'POST', body: JSON.stringify(form) });
      if (photo) await uploadPhoto(`/api/people/${saved.id}/photo`, photo);
      else if (removePhoto) await api(`/api/people/${saved.id}/photo`, { method: 'DELETE' });
      await qc.invalidateQueries({ queryKey: keys.people });
      // A new person goes straight to setting up their phone, as in the prototype flow.
      navigate(person ? '/family' : `/family/people/${saved.id}/setup`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!person || !window.confirm(t('person.deleteConfirm', { name: person.name }))) return;
    setBusy(true);
    try {
      await api<void>(`/api/people/${person.id}`, { method: 'DELETE' });
      await qc.invalidateQueries({ queryKey: keys.people });
      navigate('/family');
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <FamilyPage>
      <BackLink to="/family">{t('nav.family')}</BackLink>
      <h1 className="text-2xl font-bold">
        {person ? t('person.editTitle', { name: person.name }) : t('person.addTitle')}
      </h1>
      {!canEdit && <Alert>{t('person.viewerNote')}</Alert>}

      <form onSubmit={save} className="flex flex-col gap-5" noValidate>
        <fieldset disabled={!canEdit || busy} className="flex flex-col gap-5">
          <PhotoPicker
            className="w-32"
            label={t('person.photo')}
            existingPath={person?.hasPhoto && !removePhoto ? `/api/people/${person.id}/photo` : undefined}
            existingVersion={person?.photoVersion}
            file={photo}
            onFile={(f) => { setPhoto(f); setRemovePhoto(false); }}
            onRemove={() => setRemovePhoto(true)}
          />
          <TextField
            label={t('person.name')}
            hint={t('person.nameHint')}
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            maxLength={60}
            required
            error={fieldErrors?.name}
          />

          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 font-medium">{t('person.voiceLanguage')}</legend>
            <p className="text-muted">{t('person.voiceHint')}</p>
            <div className="flex flex-wrap gap-2">
              {LOCALES.map((l) => {
                const on = form.voiceLanguage === l.code;
                return (
                  <label
                    key={l.code}
                    className={`flex min-h-12 cursor-pointer items-center gap-2 rounded-full border-2 px-4 font-semibold ${on ? 'border-teal bg-teal text-white' : 'border-[#C9D3D2] bg-white text-ink'}`}
                  >
                    <input
                      type="radio"
                      name="voiceLanguage"
                      value={l.code}
                      checked={on}
                      onChange={() => setForm({ ...form, voiceLanguage: l.code })}
                      className="sr-only"
                    />
                    {on && <span aria-hidden="true">✓</span>}
                    <span lang={l.code}>{l.nativeName}</span>
                    {l.nativeName !== l.englishName && <span className="text-sm opacity-80">{l.englishName}</span>}
                  </label>
                );
              })}
            </div>
            {fieldErrors?.voiceLanguage && <p className="text-danger">{fieldErrors.voiceLanguage}</p>}
          </fieldset>

          <div className="flex flex-col gap-1">
            <label htmlFor="tz" className="font-medium">
              {t('person.timeZone')}
            </label>
            <select
              id="tz"
              value={form.timeZone}
              onChange={(e) => setForm({ ...form, timeZone: e.target.value })}
              className="min-h-14 rounded-xl border-2 border-muted/40 bg-white px-3 text-lg"
            >
              {zones.map((z) => (
                <option key={z} value={z}>
                  {z.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </div>

          <TextField
            label={t('person.phone')}
            hint={t('person.phoneHint')}
            type="tel"
            autoComplete="off"
            value={form.phone}
            onChange={(e) => setForm({ ...form, phone: e.target.value })}
            maxLength={20}
            error={fieldErrors?.phone}
          />
          <TextField
            label={t('person.familyPhone')}
            hint={t('person.familyPhoneHint')}
            type="tel"
            autoComplete="tel"
            value={form.familyPhone}
            onChange={(e) => setForm({ ...form, familyPhone: e.target.value })}
            maxLength={20}
            error={fieldErrors?.familyPhone}
          />

          <label className="flex items-start gap-3 rounded-xl bg-white p-3">
            <input type="checkbox" className="mt-1 size-5 flex-none accent-teal" checked={form.periodTracking}
              onChange={(e) => setForm({ ...form, periodTracking: e.target.checked })} />
            <span className="flex flex-col">
              <span className="font-medium">{t('person.periodTracking')}</span>
              <span className="text-sm text-muted">{t('person.periodTrackingHint')}</span>
            </span>
          </label>

          {error != null && !fieldErrors && <Alert>{t(errorKey(error))}</Alert>}
          {canEdit && (
            <Button type="submit" disabled={!form.name.trim()}>
              {t('common.save')}
            </Button>
          )}
        </fieldset>
      </form>

      {person && canEdit && (
        <button
          type="button"
          onClick={remove}
          disabled={busy}
          className="min-h-14 rounded-2xl border-2 border-danger font-semibold text-danger"
        >
          {t('person.delete')}
        </button>
      )}
    </FamilyPage>
  );
}
