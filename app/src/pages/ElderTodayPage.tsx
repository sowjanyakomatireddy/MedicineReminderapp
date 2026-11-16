import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, ApiError } from '../api/client';
import { deviceStore, type PairedDevice } from '../device/deviceStore';
import { findLocale } from '../i18n/locales';
import { MedAlarm, isNativeApp, type StoredSync, type SyncData } from '../native/medAlarm';
import { alarmProfile } from '../native/alarmConfig';
import { PillIcon } from '../components/PillIcon';
import { periodKey, todaysDoses, type ElderDose } from '../elder/today';

/**
 * The grandfather's Today screen (prototype "Today"): every dose of the day, the next one large,
 * taken ones ticked, and a big "Call family" button. Always in the person's own language.
 * On the Android app it reads the schedule stored on the phone, so it works offline.
 */
export function ElderTodayPage() {
  const device = deviceStore.get();
  if (!device) return <Navigate to="/elder/pair" replace />;
  return <Today device={device} />;
}

function useDay(device: PairedDevice) {
  const navigate = useNavigate();
  const [data, setData] = useState<StoredSync>({});
  const [offline, setOffline] = useState(false);

  const unlinked = useCallback(() => {
    if (isNativeApp()) void MedAlarm.cancelAll();
    deviceStore.set(null);
    navigate('/elder/pair', { replace: true });
  }, [navigate]);

  const refresh = useCallback(async () => {
    if (isNativeApp()) {
      setData(await MedAlarm.getSyncData());
      try {
        setData(await MedAlarm.syncNow());
        setOffline(false);
      } catch (e) {
        if ((e as { code?: string }).code === 'UNLINKED') return unlinked();
        setOffline(true); // keep showing the stored schedule
      }
      return;
    }
    try {
      setData({ sync: await api<SyncData>('/api/devices/me/sync', { token: device.token }), statuses: {} });
      setOffline(false);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return unlinked();
      setOffline(true);
    }
  }, [device.token, unlinked]);

  useEffect(() => {
    void refresh();
    const onVisible = () => document.visibilityState === 'visible' && void refresh();
    document.addEventListener('visibilitychange', onVisible);
    const reload = () => void MedAlarm.getSyncData().then(setData);
    const subs = isNativeApp() ? [MedAlarm.addListener('synced', reload), MedAlarm.addListener('doseLogged', reload)] : [];
    return () => {
      document.removeEventListener('visibilitychange', onVisible);
      subs.forEach((s) => void s.then((h) => h.remove()));
    };
  }, [refresh]);

  // Keep the person's words and voice on the phone up to date (the family may have changed the language).
  const person = data.sync?.person;
  useEffect(() => {
    if (!person) return;
    deviceStore.set({ ...device, person: { ...device.person, ...person } });
    if (isNativeApp()) void MedAlarm.setProfile(alarmProfile(person));
  }, [person?.name, person?.voiceLanguage, person?.timeZone]); // eslint-disable-line react-hooks/exhaustive-deps

  return { data, offline };
}

function Today({ device }: { device: PairedDevice }) {
  const { i18n } = useTranslation();
  const { data, offline } = useDay(device);
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(id);
  }, []);

  const person = data.sync?.person ?? device.person;
  const lang = findLocale(person.voiceLanguage)?.code ?? 'te';
  const t = i18n.getFixedT(lang);
  const doses = data.sync ? todaysDoses(data.sync, data.statuses, now) : [];
  const familyPhone = data.sync?.person.familyPhone;
  const dateText = now.toLocaleDateString(findLocale(lang)?.bcp47, {
    weekday: 'long', day: 'numeric', month: 'long', timeZone: person.timeZone,
  });

  return (
    <main lang={lang} className="mx-auto flex min-h-dvh max-w-md flex-col gap-4 px-5 pt-10 pb-6 text-[20px]">
      <header className="flex flex-col gap-1 px-1">
        <h1 className="text-[36px] leading-tight font-bold">{t('elder.today')}</h1>
        <p className="text-[20px] text-muted">{dateText}</p>
      </header>

      {offline && <p role="status" className="rounded-2xl bg-white p-3 text-[20px] text-muted">{t('elder.offline')}</p>}
      {data.sync && doses.length === 0 && <p className="rounded-3xl bg-white p-6 text-[22px]">{t('elder.noneToday')}</p>}
      {!data.sync && !offline && <p role="status" className="p-2">{t('common.loading')}</p>}

      <ol className="flex flex-col gap-3">
        {doses.map((d) => <DoseCard key={d.key} dose={d} t={t} />)}
      </ol>

      <div className="flex-1" />
      {familyPhone && (
        <a href={`tel:${familyPhone.replace(/[^+0-9]/g, '')}`}
          className="flex min-h-16 items-center justify-center gap-3 rounded-[22px] border-2 border-teal bg-white text-[22px] font-bold text-teal">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"
            strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" />
          </svg>
          {t('elder.callFamily')}
        </a>
      )}
      {isNativeApp() && (
        <Link to="/elder/setup" className="flex min-h-14 items-center self-center px-4 text-[20px] text-muted underline">{t('elder.setupLink')}</Link>
      )}
    </main>
  );
}

function DoseCard({ dose: d, t }: { dose: ElderDose; t: (k: string) => string }) {
  const [h, m] = d.time.split(':').map(Number);
  const clock = `${((h + 11) % 12) + 1}:${String(m).padStart(2, '0')}`;
  const card = d.next
    ? 'border-[3px] border-teal bg-white'
    : d.status === 'TAKEN' ? 'bg-[#EAF5EC]' : d.status === 'MISSED' ? 'border-2 border-danger bg-white' : 'bg-white';
  return (
    <li className={`flex flex-col gap-3 rounded-3xl p-[18px] ${card}`}>
      <div className="flex items-center justify-between gap-2">
        {d.next && <span className="rounded-full bg-teal px-3.5 py-1 text-[20px] font-semibold text-white">{t('elder.next')}</span>}
        {d.status === 'TAKEN' && (
          <span className="rounded-full bg-[#1B7A34] px-3.5 py-1 text-[20px] font-semibold text-white">✓ {t('elder.takenShort')}</span>
        )}
        {d.status === 'MISSED' && (
          <span className="rounded-full bg-danger px-3.5 py-1 text-[20px] font-semibold text-white">{t('elder.missedShort')}</span>
        )}
        <span className={`ml-auto font-bold ${d.next ? 'text-[26px]' : 'text-xl text-muted'}`}>
          <span lang="en">{clock}</span> {t(`alarm.${periodKey(d.time)}`)}
        </span>
      </div>
      <div className="flex items-center gap-4">
        <PillIcon shape={d.shape} color={d.color} size={72} />
        <div className="flex flex-col gap-1.5">
          <span className="text-[26px] leading-snug font-bold">{d.title}</span>
          {d.food !== 'ANY' && (
            <span className={`self-start rounded-full px-3 py-1 text-[20px] font-semibold ${d.food === 'BEFORE' ? 'bg-before-bg text-before-fg' : 'bg-after-bg text-after-fg'}`}>
              {t(`food.${d.food}`)}
            </span>
          )}
        </div>
      </div>
    </li>
  );
}
