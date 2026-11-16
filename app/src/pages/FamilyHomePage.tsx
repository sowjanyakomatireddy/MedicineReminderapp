import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { api, ApiError, errorKey } from '../api/client';
import { keys, useFamily, usePeople, type Family } from '../api/family';
import { useToday, type Dose, type DoseStatus } from '../api/medicines';
import { useAlerts } from '../api/doses';
import { AlertsCard } from '../components/AlertsCard';
import { findLocale } from '../i18n/locales';
import { PillIcon } from '../components/PillIcon';
import { PersonChips } from '../components/PersonChips';
import { formatTime } from '../components/format';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { Alert, Button, TextField } from '../components/ui';
import { ErrorState, FamilyPage, Loading } from '../components/layout';

export function FamilyHomePage() {
  const family = useFamily();

  if (family.isPending) return <Loading />;
  if (family.isError) {
    return (
      <FamilyPage nav={false}>
        <ErrorState onRetry={() => family.refetch()} />
      </FamilyPage>
    );
  }
  if (!family.data) return <StartFamily />;
  return <Home family={family.data} />;
}

/** Family Home: pick a person, see today's doses and their status. */
function Home({ family }: { family: Family }) {
  const { t, i18n } = useTranslation();
  const { logout } = useAuth();
  const [params, setParams] = useSearchParams();
  const people = usePeople();
  const today = useToday();
  const alerts = useAlerts();
  const isEditor = family.myRole === 'EDITOR';
  const personId = params.get('person') ?? people.data?.[0]?.id;
  const day = today.data?.find((d) => d.personId === personId);
  const lang = findLocale(day?.voiceLanguage);

  return (
    <FamilyPage>
      <header className="flex items-center justify-between">
        <span className="text-xl font-bold text-teal-dark">{family.name}</span>
        <LanguageSwitcher />
      </header>
      <h1 className="sr-only">{t('nav.home')}</h1>

      {alerts.data && alerts.data.length > 0 && (
        <section aria-labelledby="alerts-now" className="flex flex-col gap-2">
          <h2 id="alerts-now" className="sr-only">{t('home.alerts')}</h2>
          {alerts.data.map((a) => (
            <Link key={a.id} to={`/missed/${a.id}`}
              className="flex items-center gap-3 rounded-[18px] border-2 border-danger bg-[#FBE9E7] px-4 py-3">
              <span aria-hidden="true" className="flex size-9 flex-none items-center justify-center rounded-full bg-danger text-lg font-bold text-white">!</span>
              <span className="flex flex-1 flex-col">
                <span className="font-bold">{t('missed.heading', { name: a.personName, time: formatTime(a.time, i18n.resolvedLanguage) })}</span>
                <span className="text-sm text-[#7A1F16]">{a.voiceName ?? a.medicineName}</span>
              </span>
              <span aria-hidden="true" className="font-bold text-danger">→</span>
            </Link>
          ))}
        </section>
      )}
      <AlertsCard />

      {people.data && (
        <PersonChips people={people.data} selected={personId} canAdd={isEditor}
          onSelect={(id) => setParams({ person: id }, { replace: true })} />
      )}
      {people.data?.length === 0 && (
        <div className="flex flex-col gap-3 rounded-[18px] bg-white p-4">
          <p>{t('home.noPeople')}</p>
          {isEditor && (
            <Link to="/family/people/new" className="flex min-h-14 items-center justify-center rounded-2xl bg-teal font-semibold text-white">
              {t('home.addFirstPerson')}
            </Link>
          )}
        </div>
      )}
      {(people.isError || today.isError) && (
        <ErrorState onRetry={() => { void people.refetch(); void today.refetch(); }} />
      )}
      {personId && today.isPending && <Loading />}

      {day && (
        <>
          <section aria-labelledby="today-heading" className="flex flex-col gap-2.5 rounded-3xl bg-teal-dark p-5 text-white">
            <h2 id="today-heading" className="text-sm font-semibold tracking-[0.04em] text-[#BFE3DE] uppercase">
              {t('home.today', { name: day.name, language: lang?.englishName ?? day.voiceLanguage })}
            </h2>
            <p className="flex items-baseline gap-2">
              <span className="text-[44px] leading-none font-bold">{t('home.takenOf', { taken: day.taken, total: day.total })}</span>
              <span className="text-lg text-[#DCEFEC]">{t('home.medicinesTaken')}</span>
            </p>
            {day.total > 0 && (
              <div className="flex gap-1.5" aria-hidden="true">
                {day.doses.map((d, i) => (
                  <span key={d.scheduleId} className={`h-2 flex-1 rounded ${i < day.taken ? 'bg-[#7FD1C4]' : 'bg-[#2B6468]'}`} />
                ))}
              </div>
            )}
          </section>

          <section aria-labelledby="schedule-heading" className="flex flex-col gap-2.5">
            <h2 id="schedule-heading" className="text-lg font-bold">{t('home.schedule')}</h2>
            {day.doses.length === 0 && (
              <p className="rounded-[18px] bg-white p-4 text-muted">{t('home.noMedicines', { name: day.name })}</p>
            )}
            <ul className="flex flex-col gap-2.5">
              {day.doses.map((d) => (
                <li key={d.scheduleId} className="flex items-center gap-3.5 rounded-[18px] bg-white px-4 py-3.5">
                  <PillIcon shape={d.shape} color={d.color} photoVersion={d.photoVersion}
                    photoPath={d.hasPhoto ? `/api/medicines/${d.medicineId}/photo` : undefined} />
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="text-[17px] font-semibold">
                      {d.voiceName ? <><span lang={day.voiceLanguage}>{d.voiceName}</span> · {d.name}</> : d.name}
                    </span>
                    <span className="text-sm text-muted">
                      {t('home.doseLine', {
                        time: formatTime(d.time, i18n.resolvedLanguage),
                        food: t(`food.${d.food}`),
                        alert: formatTime(d.alertAt, i18n.resolvedLanguage),
                      })}
                    </span>
                  </div>
                  <StatusChip dose={d} />
                </li>
              ))}
            </ul>
          </section>

          {isEditor && (
            <Link to={`/medicines/new?person=${day.personId}`}
              className="flex min-h-[52px] items-center justify-center gap-2 rounded-2xl bg-teal text-[17px] font-bold text-white">
              <span aria-hidden="true">+</span> {t('home.addMedicine')}
            </Link>
          )}
        </>
      )}

      <p className="text-sm text-muted">{t('common.disclaimer')}</p>
      <button onClick={logout} className="min-h-12 font-semibold text-teal underline">
        {t('common.logout')}
      </button>
    </FamilyPage>
  );
}

/** Status is written out in words; colour only repeats it. A missed dose opens its alert screen. */
function StatusChip({ dose }: { dose: Dose }) {
  const { t } = useTranslation();
  const styles: Record<DoseStatus, string> = {
    UPCOMING: 'bg-[#E8EEF0] text-[#34494E]',
    DUE: 'bg-before-bg text-before-fg',
    UNCONFIRMED: 'border border-[#C9D3D2] bg-white text-muted',
    TAKEN: 'bg-[#DDF0E0] text-[#1B4D22]',
    MISSED: 'bg-danger text-white',
  };
  const cls = `flex-none rounded-full px-2.5 py-1 text-[13px] font-semibold ${styles[dose.status]}`;
  const label = `${dose.status === 'TAKEN' ? '✓ ' : dose.status === 'MISSED' ? '! ' : ''}${t(`status.${dose.status}`)}`;
  if (dose.status === 'MISSED' && dose.doseLogId) {
    return <Link to={`/missed/${dose.doseLogId}`} className={`${cls} flex min-h-11 items-center underline`}>{label}</Link>;
  }
  return <span className={cls}>{label}</span>;
}

/** First visit: create a family, or join one with an invite code from a relative. */
function StartFamily() {
  const { t } = useTranslation();
  const { logout } = useAuth();
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState<{ err: unknown; form: 'create' | 'join' } | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent, form: 'create' | 'join') {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const fam =
        form === 'create'
          ? await api<Family>('/api/families', { method: 'POST', body: JSON.stringify({ name }) })
          : await api<Family>('/api/pairing/join', { method: 'POST', body: JSON.stringify({ code }) });
      qc.setQueryData(keys.family, fam);
    } catch (err) {
      setError({ err, form });
    } finally {
      setBusy(false);
    }
  }

  const message = (form: 'create' | 'join') => {
    if (error?.form !== form) return null;
    if (error.err instanceof ApiError && error.err.fieldErrors) return null;
    return <Alert>{t(errorKey(error.err, { 400: 'errors.badCode', 409: 'errors.alreadyInFamily' }))}</Alert>;
  };

  return (
    <FamilyPage nav={false}>
      <header className="flex items-center justify-between">
        <span className="text-xl font-bold text-teal-dark">{t('appName')}</span>
        <LanguageSwitcher />
      </header>
      <h1 className="text-3xl font-bold">{t('start.title')}</h1>
      <p className="text-muted">{t('start.intro')}</p>

      <form onSubmit={(e) => submit(e, 'create')} className="flex flex-col gap-4 rounded-[18px] bg-white p-4" noValidate>
        <h2 className="text-xl font-bold">{t('start.createTitle')}</h2>
        <TextField
          label={t('start.familyName')}
          hint={t('start.familyNameHint')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={80}
          required
          error={error?.form === 'create' && error.err instanceof ApiError ? error.err.fieldErrors?.name : undefined}
        />
        {message('create')}
        <Button type="submit" disabled={busy || !name.trim()}>
          {t('start.create')}
        </Button>
      </form>

      <form onSubmit={(e) => submit(e, 'join')} className="flex flex-col gap-4 rounded-[18px] bg-white p-4" noValidate>
        <h2 className="text-xl font-bold">{t('start.joinTitle')}</h2>
        <p className="text-muted">{t('start.joinIntro')}</p>
        <TextField
          label={t('start.code')}
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          autoCapitalize="characters"
          autoComplete="off"
          spellCheck={false}
          maxLength={12}
          className="min-h-14 rounded-xl border-2 border-muted/40 bg-white px-4 font-mono text-xl tracking-widest focus:border-teal"
        />
        {message('join')}
        <Button type="submit" disabled={busy || code.replace(/[^A-Za-z0-9]/g, '').length < 8}>
          {t('start.join')}
        </Button>
      </form>

      <button onClick={logout} className="min-h-12 font-semibold text-teal underline">
        {t('common.logout')}
      </button>
    </FamilyPage>
  );
}
