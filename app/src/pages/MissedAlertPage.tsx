import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../api/client';
import { doseKeys, useDose, useWeek, type DaySummary, type DoseDetail } from '../api/doses';
import { medicineKeys, type Day } from '../api/medicines';
import { Alert } from '../components/ui';
import { PillIcon } from '../components/PillIcon';
import { BackLink, FamilyPage, Loading } from '../components/layout';
import { dayName, formatTime } from '../components/format';

/** The Missed dose alert (prototype "Missed alert"): call, play the reminder again, or mark as taken. */
export function MissedAlertPage() {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const dose = useDose(id);
  if (dose.isPending) return <Loading />;
  if (dose.isError) {
    return (
      <FamilyPage>
        <BackLink to="/">{t('nav.home')}</BackLink>
        <Alert>{t('errors.loadFailed')}</Alert>
      </FamilyPage>
    );
  }
  return <Missed dose={dose.data} />;
}

function minutesLate(d: DoseDetail, now = Date.now()) {
  const [y, mo, day] = d.date.split('-').map(Number);
  const [h, m] = d.time.split(':').map(Number);
  // The dose time is the person's local time; close enough for "N min late" on the same day.
  return Math.max(0, Math.round((now - new Date(y, mo - 1, day, h, m).getTime()) / 60_000));
}

function Missed({ dose: d }: { dose: DoseDetail }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [replayMsg, setReplayMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const name = d.voiceName ? `${d.voiceName} · ${d.medicineName}` : d.medicineName;
  const time = formatTime(d.time, i18n.resolvedLanguage);
  const taken = d.status === 'TAKEN';

  const markTaken = useMutation({
    mutationFn: () => api<DoseDetail>(`/api/dose-logs/${d.id}/taken`, { method: 'POST' }),
    onSuccess: async (updated) => {
      qc.setQueryData(doseKeys.one(d.id), updated);
      await Promise.all([
        qc.invalidateQueries({ queryKey: doseKeys.alerts }),
        qc.invalidateQueries({ queryKey: medicineKeys.today }),
        qc.invalidateQueries({ queryKey: doseKeys.week(d.personId) }),
      ]);
      navigate(`/?person=${d.personId}`);
    },
  });

  const replay = useMutation({
    mutationFn: () => api<void>(`/api/dose-logs/${d.id}/replay`, { method: 'POST' }),
    onSuccess: () => setReplayMsg({ ok: true, text: t('missed.replaying', { name: d.personName }) }),
    onError: (e) =>
      setReplayMsg({
        ok: false,
        text: e instanceof ApiError && e.status === 503
          ? t('missed.replayNoPush')
          : e instanceof ApiError && e.status === 409
            ? t('missed.replayNoPhone', { name: d.personName })
            : t('errors.generic'),
      }),
  });

  return (
    <FamilyPage>
      <BackLink to="/">{t('nav.home')}</BackLink>

      {/* What the alert said, so the screen makes sense when opened from the notification. */}
      <div className="flex gap-3 rounded-[20px] border border-[#E4E9E8] bg-white p-4">
        <span aria-hidden="true" className="flex size-10 flex-none items-center justify-center rounded-[10px] bg-teal">
          <span className="block h-3 w-[22px] -rotate-[35deg] rounded-md bg-white" />
        </span>
        <div className="flex flex-col gap-0.5">
          <span className="text-xs font-semibold tracking-[0.04em] text-muted uppercase">{t('appName')}</span>
          <h1 className="font-bold">{t('missed.heading', { name: d.personName, time })}</h1>
          <p className="text-sm text-muted">
            {[d.medicineName, d.food && t(`food.${d.food}`), !taken && t('missed.late', { count: minutesLate(d) })]
              .filter(Boolean).join(' · ')}
          </p>
        </div>
      </div>

      <section className={`flex flex-col gap-3.5 rounded-3xl p-5 ${taken ? 'bg-[#EAF5EC]' : 'bg-[#FBE9E7]'}`}>
        <div className="flex items-center gap-3.5">
          <span className="flex size-16 flex-none items-center justify-center rounded-full bg-white">
            <PillIcon shape={d.shape} color={d.color} size={56}
              photoPath={d.hasPhoto && d.medicineId ? `/api/medicines/${d.medicineId}/photo` : undefined}
              photoVersion={d.photoVersion} />
          </span>
          <div className="flex flex-col gap-0.5">
            <span className="text-[22px] font-bold">{name}</span>
            <span className={`text-[15px] font-semibold ${taken ? 'text-after-fg' : 'text-[#7A1F16]'}`}>
              {taken
                ? `✓ ${t('missed.takenNow')}`
                : d.rings ? t('missed.rang', { count: d.rings }) : t('status.MISSED')}
            </span>
          </div>
        </div>

        {!taken && (
          <>
            {d.personPhone ? (
              <a href={`tel:${d.personPhone.replace(/[^+0-9]/g, '')}`}
                className="flex min-h-[54px] items-center justify-center gap-2.5 rounded-2xl bg-danger text-[17px] font-bold text-white">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"
                  strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" />
                </svg>
                {t('missed.call', { name: d.personName })}
              </a>
            ) : (
              <p className="text-sm">
                {t('missed.noPhone', { name: d.personName })}{' '}
                <Link to={`/family/people/${d.personId}`} className="font-semibold text-teal underline">{t('common.edit')}</Link>
              </p>
            )}
            <button type="button" onClick={() => replay.mutate()} disabled={replay.isPending || !d.phoneLinked}
              className="min-h-[50px] rounded-2xl border-2 border-danger bg-white text-base font-bold text-danger disabled:opacity-60">
              {t('missed.replay')}
            </button>
            {replayMsg && (
              <p role={replayMsg.ok ? 'status' : 'alert'}
                className={`rounded-xl bg-white p-3 text-sm font-semibold ${replayMsg.ok ? 'text-after-fg' : 'text-danger'}`}>
                {replayMsg.text}
              </p>
            )}
            <button type="button" onClick={() => markTaken.mutate()} disabled={markTaken.isPending}
              className="min-h-11 text-[15px] font-semibold text-ink underline">
              {t('missed.markTaken')}
            </button>
            {markTaken.isError && <Alert>{t('errors.generic')}</Alert>}
          </>
        )}
      </section>

      <Week personId={d.personId} />
      <p className="text-center text-[13px] text-muted">{t('missed.footer')}</p>
    </FamilyPage>
  );
}

/** JavaScript's getDay(): Sunday = 0. */
const BY_JS_DAY: Day[] = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

/** "This week": one circle per day. Meaning is in the symbol and the label, not only the colour. */
function Week({ personId }: { personId: string }) {
  const { t, i18n } = useTranslation();
  const week = useWeek(personId);
  if (!week.data) return null;
  const today = week.data[week.data.length - 1];
  const look = (d: DaySummary) =>
    d.missed > 0
      ? { cls: 'bg-[#F6D3CF] border-2 border-danger text-danger', mark: '!', label: t('missed.weekMissed', { count: d.missed }) }
      : d.total > 0 && d.taken >= d.total
        ? { cls: 'bg-teal text-white', mark: '✓', label: t('missed.weekAll') }
        : { cls: 'bg-[#E4E9E8] text-muted', mark: '', label: t('missed.weekNotYet') };
  return (
    <section aria-labelledby="week-heading" className="flex flex-col gap-2.5">
      <h2 id="week-heading" className="text-lg font-bold">{t('missed.week')}</h2>
      <div className="flex flex-col gap-3 rounded-[18px] bg-white p-4">
        <ol className="flex justify-between">
          {week.data.map((d) => {
            const l = look(d);
            const day = dayName(BY_JS_DAY[new Date(`${d.date}T12:00:00`).getDay()], i18n.resolvedLanguage);
            return (
              <li key={d.date} className="flex flex-col items-center gap-1.5">
                <span className="text-xs font-semibold text-muted" aria-hidden="true">{day}</span>
                <span aria-hidden="true" className={`flex size-8 items-center justify-center rounded-full text-sm font-bold ${l.cls}`}>
                  {l.mark}
                </span>
                <span className="sr-only">{`${day}: ${l.label}`}</span>
              </li>
            );
          })}
        </ol>
        <p className="text-[13px] text-muted">{t('missed.weekToday', { taken: today.taken, total: today.total })}</p>
        <p className="flex flex-wrap gap-x-3.5 gap-y-1 text-[13px] text-muted" aria-hidden="true">
          <span>✓ {t('missed.weekAll')}</span>
          <span>! {t('missed.weekMissedLegend')}</span>
        </p>
      </div>
    </section>
  );
}
