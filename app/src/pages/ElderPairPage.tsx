import { useId, useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { API_BASE, api, errorKey } from '../api/client';
import { deviceStore, type PairedPerson } from '../device/deviceStore';
import { LOCALES, findLocale } from '../i18n/locales';
import { MedAlarm, isNativeApp } from '../native/medAlarm';
import { alarmProfile } from '../native/alarmConfig';

interface Lookup {
  familyName: string;
  purpose: 'DEVICE' | 'MEMBER';
  people: { id: string; name: string; voiceLanguage: string }[];
}
interface Redeemed {
  deviceToken: string;
  deviceId: string;
  familyName: string;
  person: PairedPerson;
}

/**
 * Done once, usually by a family member holding the elder's phone:
 * type the family code, then pick which person this phone belongs to.
 * Shown in Telugu first, with a language picker for whoever is setting it up.
 */
export function ElderPairPage() {
  const { i18n } = useTranslation();
  const navigate = useNavigate();
  const [lang, setLang] = useState('te');
  const t = i18n.getFixedT(lang);
  const [code, setCode] = useState('');
  const [found, setFound] = useState<Lookup | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const codeId = useId();
  const langId = useId();

  if (deviceStore.get()) return <Navigate to="/elder" replace />;

  async function lookup(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await api<Lookup>('/api/pairing/lookup', { method: 'POST', body: JSON.stringify({ code }), token: null });
      if (r.purpose !== 'DEVICE') setError('errors.badCode');
      else setFound(r);
    } catch (err) {
      setError(errorKey(err, { 400: 'errors.badCode' }));
    } finally {
      setBusy(false);
    }
  }

  async function choose(personId: string) {
    setBusy(true);
    setError(null);
    try {
      const native = isNativeApp();
      const phone = native ? await MedAlarm.getStatus().catch(() => null) : null;
      const r = await api<Redeemed>('/api/pairing/redeem', {
        method: 'POST',
        body: JSON.stringify({
          code,
          personId,
          platform: native ? 'android' : 'web',
          deviceName: phone ? `${phone.manufacturer} ${phone.model}`.slice(0, 80) : undefined,
        }),
        token: null,
      });
      deviceStore.set({ token: r.deviceToken, deviceId: r.deviceId, familyName: r.familyName, person: r.person });
      if (native) {
        // Let the phone sync by itself (push, daily, after restart) and build alarms in the person's language.
        await MedAlarm.setDevice({ apiBase: API_BASE, token: r.deviceToken });
        await MedAlarm.setProfile(alarmProfile(r.person));
        void MedAlarm.syncNow().catch(() => {});
      }
      // On the Android app, go straight to the permission checklist and test alarm.
      navigate(native ? '/elder/setup' : '/elder', { replace: true });
    } catch (err) {
      setError(errorKey(err, { 400: 'errors.badCode' }));
      setBusy(false);
    }
  }

  return (
    <main lang={lang} className="mx-auto flex min-h-dvh max-w-md flex-col gap-6 px-5 py-8 text-[20px]">
      <div className="flex items-center justify-end gap-2">
        <label htmlFor={langId} className="text-[20px] text-muted">
          {t('common.language')}
        </label>
        <select
          id={langId}
          value={lang}
          onChange={(e) => setLang(e.target.value)}
          className="min-h-14 rounded-xl border-2 border-muted/40 bg-white px-3 text-[20px]"
        >
          {LOCALES.map((l) => (
            <option key={l.code} value={l.code} lang={l.code}>
              {l.nativeName}
            </option>
          ))}
        </select>
      </div>

      {!found ? (
        <form onSubmit={lookup} className="flex flex-col gap-5" noValidate>
          <h1 className="text-[32px] font-bold text-teal-dark">{t('elder.pairTitle')}</h1>
          <p>{t('elder.pairIntro')}</p>
          <label htmlFor={codeId} className="font-semibold">
            {t('elder.code')}
          </label>
          <input
            id={codeId}
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            autoCapitalize="characters"
            autoComplete="off"
            spellCheck={false}
            maxLength={12}
            placeholder="XXXX-XXXX"
            className="min-h-[72px] rounded-2xl border-2 border-muted/40 bg-white px-4 text-center font-mono text-[32px] tracking-[0.12em] focus:border-teal"
          />
          {error && <ElderAlert>{t(error)}</ElderAlert>}
          <button
            type="submit"
            disabled={busy || code.replace(/[^A-Za-z0-9]/g, '').length < 8}
            className="min-h-[76px] rounded-[22px] bg-teal text-[28px] font-bold text-white disabled:opacity-75"
          >
            {t('elder.next')}
          </button>
        </form>
      ) : (
        <section aria-labelledby="who" className="flex flex-col gap-4">
          <p className="text-muted">{found.familyName}</p>
          <h1 id="who" className="text-[32px] font-bold text-teal-dark">
            {t('elder.whoTitle')}
          </h1>
          {found.people.length === 0 && <ElderAlert>{t('elder.noPeople')}</ElderAlert>}
          <ul className="flex flex-col gap-3">
            {found.people.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => choose(p.id)}
                  className="flex min-h-[76px] w-full flex-col items-start justify-center rounded-[22px] border-2 border-teal bg-white px-5 py-3 text-left disabled:opacity-75"
                >
                  <span className="text-[28px] font-bold">{p.name}</span>
                  <span className="text-[20px] text-muted" lang={p.voiceLanguage}>
                    {findLocale(p.voiceLanguage)?.nativeName ?? p.voiceLanguage}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {error && <ElderAlert>{t(error)}</ElderAlert>}
          <button
            type="button"
            onClick={() => {
              setFound(null);
              setError(null);
            }}
            className="min-h-14 rounded-[22px] border-2 border-teal font-semibold text-teal"
          >
            {t('elder.back')}
          </button>
        </section>
      )}
    </main>
  );
}

function ElderAlert({ children }: { children: string }) {
  return (
    <p role="alert" className="rounded-2xl border-2 border-danger bg-white p-4 font-semibold text-danger">
      {children}
    </p>
  );
}
