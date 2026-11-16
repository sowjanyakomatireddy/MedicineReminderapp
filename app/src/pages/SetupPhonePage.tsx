import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, errorKey } from '../api/client';
import { createCode, keys, useDevices, useFamily, usePerson, type PairingCode } from '../api/family';
import { Alert, Button } from '../components/ui';
import { CodeCard } from '../components/CodeCard';
import { BackLink, FamilyPage, Loading, SectionHeading } from '../components/layout';

const STEPS = [1, 2, 3, 4, 5] as const;
type Step = (typeof STEPS)[number];

/**
 * Ticks are remembered on this device only (a per-viewer convenience).
 * In Phase 4 the phone itself reports which permissions are really granted.
 */
function useTicks(personId: string) {
  const key = `mr.setup.${personId}`;
  const [ticks, setTicks] = useState<Partial<Record<Step, boolean>>>(() => {
    try {
      return JSON.parse(localStorage.getItem(key) ?? '{}');
    } catch {
      return {};
    }
  });
  const toggle = (s: Step) => {
    const next = { ...ticks, [s]: !ticks[s] };
    setTicks(next);
    try {
      localStorage.setItem(key, JSON.stringify(next));
    } catch {
      /* ignore */
    }
  };
  return [ticks, toggle] as const;
}

export function SetupPhonePage() {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const person = usePerson(id);
  const family = useFamily();
  const devices = useDevices(id);
  const [ticks, toggle] = useTicks(id);

  if (person.isPending || family.isPending) return <Loading />;
  if (person.isError || !person.data) {
    return (
      <FamilyPage>
        <BackLink to="/family">{t('nav.family')}</BackLink>
        <Alert>{t('errors.loadFailed')}</Alert>
      </FamilyPage>
    );
  }
  const p = person.data;
  const isEditor = family.data?.myRole === 'EDITOR';
  // Step 1 is done for real once a phone is linked.
  const isDone = (s: Step) => (s === 1 && p.linkedPhones > 0) || !!ticks[s];
  const doneCount = STEPS.filter(isDone).length;

  return (
    <FamilyPage>
      <BackLink to="/family">{t('nav.family')}</BackLink>
      <div className="flex flex-col gap-1.5">
        <h1 className="text-2xl font-bold">{t('setup.title', { name: p.name })}</h1>
        <p className="text-[15px] leading-relaxed text-[#34494E]">{t('setup.intro')}</p>
      </div>

      <div className="flex flex-col gap-2.5 rounded-[18px] bg-teal-dark px-4 py-3.5 text-white">
        <p className="font-bold" id="setup-progress">
          {t('setup.progress', { done: doneCount, total: STEPS.length })}
        </p>
        <div
          role="progressbar"
          aria-labelledby="setup-progress"
          aria-valuemin={0}
          aria-valuemax={STEPS.length}
          aria-valuenow={doneCount}
          className="h-2 overflow-hidden rounded bg-[#2B6468]"
        >
          <div className="h-2 rounded bg-[#7FD1C4]" style={{ width: `${(doneCount / STEPS.length) * 100}%` }} />
        </div>
      </div>

      <ol className="flex flex-col gap-2">
        {STEPS.map((s) => {
          const on = isDone(s);
          const auto = s === 1 && p.linkedPhones > 0;
          return (
            <li key={s}>
              <button
                type="button"
                aria-pressed={on}
                disabled={auto}
                onClick={() => toggle(s)}
                className={`flex min-h-16 w-full items-center gap-3 rounded-2xl border px-3.5 py-2.5 text-left ${on ? 'border-[#BFE0C4] bg-[#EAF5EC]' : 'border-[#C9D3D2] bg-white'}`}
              >
                <span
                  aria-hidden="true"
                  className={`flex size-8 flex-none items-center justify-center rounded-full text-[15px] font-bold ${on ? 'bg-[#1B7A34] text-white' : 'bg-[#E4E9E8] text-ink'}`}
                >
                  {on ? '✓' : s}
                </span>
                <span className="flex flex-1 flex-col gap-0.5">
                  <span className="font-bold">{t(`setup.step${s}Title`)}</span>
                  <span className="text-[13px] leading-snug text-muted">{t(`setup.step${s}Hint`)}</span>
                </span>
                <span className="sr-only">{on ? t('setup.done') : t('setup.notDone')}</span>
              </button>
            </li>
          );
        })}
      </ol>

      {isEditor && <PhoneCode />}
      <LinkedPhones personId={p.id} canUnlink={isEditor} devices={devices.data ?? []} />

      <p className="text-[13px] leading-relaxed text-muted">{t('setup.tapHint')}</p>

      <div className="flex flex-col gap-1">
        <button
          type="button"
          disabled
          aria-describedby="test-alarm-note"
          className="flex min-h-[54px] items-center justify-center gap-2.5 rounded-2xl bg-teal text-[17px] font-bold text-white opacity-60"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"
            strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M4 9v6h4l5 4V5L8 9H4z" />
            <path d="M16.5 8.5a5 5 0 0 1 0 7" />
          </svg>
          {t('setup.testAlarm')}
        </button>
        <p id="test-alarm-note" className="text-center text-sm text-muted">
          {t('setup.testAlarmLater')}
        </p>
      </div>
    </FamilyPage>
  );
}

function PhoneCode() {
  const { t } = useTranslation();
  const [code, setCode] = useState<PairingCode | null>(null);
  const make = useMutation({ mutationFn: () => createCode('DEVICE'), onSuccess: setCode });
  const url = `${window.location.origin}/elder/pair`;
  return (
    <section aria-labelledby="code-heading" className="flex flex-col gap-3">
      <SectionHeading>
        <span id="code-heading">{t('setup.codeHeading')}</span>
      </SectionHeading>
      {code ? (
        <CodeCard code={code} help={t('setup.codeHelp', { url })} />
      ) : (
        <Button type="button" onClick={() => make.mutate()} disabled={make.isPending}>
          {t('setup.makeCode')}
        </Button>
      )}
      {make.error && <Alert>{t(errorKey(make.error))}</Alert>}
    </section>
  );
}

function LinkedPhones({
  personId,
  canUnlink,
  devices,
}: {
  personId: string;
  canUnlink: boolean;
  devices: { id: string; name?: string; createdAt: string }[];
}) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const unlink = useMutation({
    mutationFn: (deviceId: string) => api<void>(`/api/devices/${deviceId}`, { method: 'DELETE' }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.devices(personId) });
      void qc.invalidateQueries({ queryKey: keys.people });
    },
  });
  if (devices.length === 0) return null;
  return (
    <section aria-labelledby="linked-heading" className="flex flex-col gap-2">
      <SectionHeading>
        <span id="linked-heading">{t('setup.linkedHeading')}</span>
      </SectionHeading>
      <ul className="flex flex-col rounded-[18px] bg-white px-4 py-1">
        {devices.map((d) => (
          <li key={d.id} className="flex items-center justify-between gap-3 border-b border-[#E4E9E8] py-3 last:border-0">
            <span className="flex flex-col">
              <span className="font-semibold">{d.name ?? t('setup.unknownPhone')}</span>
              <span className="text-sm text-muted">
                {t('setup.linkedOn', {
                  date: new Date(d.createdAt).toLocaleDateString(i18n.resolvedLanguage, { day: 'numeric', month: 'short' }),
                })}
              </span>
            </span>
            {canUnlink && (
              <button
                type="button"
                className="min-h-11 px-2 font-semibold text-danger underline"
                disabled={unlink.isPending}
                onClick={() => window.confirm(t('setup.unlinkConfirm')) && unlink.mutate(d.id)}
              >
                {t('setup.unlink')}
              </button>
            )}
          </li>
        ))}
      </ul>
      {unlink.error && <Alert>{t(errorKey(unlink.error))}</Alert>}
    </section>
  );
}
