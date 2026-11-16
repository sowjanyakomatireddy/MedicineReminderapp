import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { deviceStore, type PairedPerson } from '../device/deviceStore';
import { findLocale } from '../i18n/locales';
import { MedAlarm, isNativeApp, type PhoneStatus, type SettingsTarget, type VoiceStatus } from '../native/medAlarm';
import { alarmProfile, testAlarm, voiceLocale } from '../native/alarmConfig';

/**
 * On the elder's phone, right after pairing: shows what is already allowed and opens the right settings
 * page for each step, checks the Text-to-Speech voice, and rings a test alarm in 10 seconds.
 * Large text, one action per row, in the person's own language.
 */
export function ElderSetupPage() {
  const device = deviceStore.get();
  if (!device) return <Navigate to="/elder/pair" replace />;
  return <Setup person={device.person} />;
}

function Setup({ person }: { person: PairedPerson }) {
  const { i18n } = useTranslation();
  const lang = findLocale(person.voiceLanguage)?.code ?? 'te';
  const t = i18n.getFixedT(lang);
  const native = isNativeApp();
  const [status, setStatus] = useState<PhoneStatus | null>(null);
  const [voice, setVoice] = useState<VoiceStatus | null>(null);
  const [testSet, setTestSet] = useState(false);

  const refresh = useCallback(async () => {
    if (!native) return;
    setStatus(await MedAlarm.getStatus());
    setVoice((await MedAlarm.checkVoice({ lang: voiceLocale(person.voiceLanguage) })).status);
  }, [native, person.voiceLanguage]);

  useEffect(() => {
    if (!native) return;
    void MedAlarm.setProfile(alarmProfile(person));
    void refresh();
    // Coming back from a settings page: check again.
    const onVisible = () => document.visibilityState === 'visible' && void refresh();
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [native, person, refresh]);

  const open = (target: SettingsTarget) => () => void MedAlarm.openSettings({ target });
  const languageName = findLocale(person.voiceLanguage)?.nativeName ?? person.voiceLanguage;

  async function ringTest() {
    await MedAlarm.testAlarm({ seconds: 10, ...testAlarm(person) });
    setTestSet(true);
  }

  return (
    <main lang={lang} className="mx-auto flex min-h-dvh max-w-md flex-col gap-5 px-4 py-8 text-[20px]">
      <h1 className="text-[30px] font-bold text-teal-dark">{t('elderSetup.title')}</h1>
      <p>{t('elderSetup.intro')}</p>

      {!native && <p role="status" className="rounded-2xl bg-white p-4">{t('elderSetup.onlyAndroid')}</p>}

      {native && status && (
        <ol className="flex flex-col gap-3">
          <Row ok={status.notifications} label={t('elderSetup.notifications')} t={t}
            action={status.notificationPermission === 'prompt' || status.notificationPermission === 'prompt-with-rationale'
              ? { text: t('elderSetup.allow'), run: async () => setStatus(await MedAlarm.requestNotifications()) }
              : { text: t('elderSetup.fix'), run: open('notifications') }} />
          <Row ok={status.exactAlarms} label={t('elderSetup.exactAlarms')} t={t}
            action={{ text: t('elderSetup.fix'), run: open('exactAlarm') }} />
          <Row ok={status.fullScreen} label={t('elderSetup.lockScreen')} t={t}
            action={{ text: t('elderSetup.fix'), run: open('fullScreen') }}>
            {status.xiaomiLockScreenPage && (
              <SmallButton onClick={open('lockScreen')}>{t('elderSetup.xiaomiLock')}</SmallButton>
            )}
          </Row>
          <Row ok={status.batteryUnrestricted} label={t('elderSetup.battery')} t={t}
            action={{ text: t('elderSetup.fix'), run: open('battery') }}>
            {status.autostartPage && (
              <>
                <p className="text-base text-muted">{t('elderSetup.autostartHint', { maker: status.manufacturer })}</p>
                <SmallButton onClick={open('autostart')}>{t('elderSetup.autostart')}</SmallButton>
              </>
            )}
          </Row>
          <Row ok={voice === 'available'} label={t('elderSetup.voice', { language: languageName })} t={t}
            action={voice === 'missingData' || voice === 'notSupported'
              ? { text: t('elderSetup.installVoice'), run: () => void MedAlarm.installVoice() }
              : undefined}>
            {voice === 'noEngine' && <p className="text-base text-danger">{t('elderSetup.noEngine')}</p>}
            {(voice === 'missingData' || voice === 'notSupported') && (
              <p className="text-base text-muted">{t('elderSetup.voiceMissing', { language: languageName })}</p>
            )}
          </Row>
        </ol>
      )}

      {native && (
        <div className="flex flex-col gap-2">
          <button type="button" onClick={ringTest}
            className="min-h-[76px] rounded-[22px] bg-teal text-[24px] font-bold text-white">
            {t('elderSetup.testAlarm')}
          </button>
          <p role="status" className={testSet ? 'font-semibold text-teal-dark' : 'text-base text-muted'}>
            {testSet ? t('elderSetup.testScheduled') : t('elderSetup.testHint')}
          </p>
        </div>
      )}

      <Link to="/elder" className="flex min-h-14 items-center justify-center rounded-[22px] border-2 border-teal font-semibold text-teal">
        {t('elderSetup.done')}
      </Link>
      {native && (
        <Link to="/elder/debug" className="self-center py-3 text-base text-muted underline">
          {t('elderSetup.details')}
        </Link>
      )}
    </main>
  );
}

function Row({ ok, label, action, children, t }: {
  ok: boolean;
  label: string;
  action?: { text: string; run: () => void };
  children?: ReactNode;
  t: (k: string) => string;
}) {
  return (
    <li className={`flex flex-col gap-2 rounded-2xl border-2 p-4 ${ok ? 'border-[#BFE0C4] bg-[#EAF5EC]' : 'border-[#C9D3D2] bg-white'}`}>
      <div className="flex items-center gap-3">
        <span aria-hidden="true"
          className={`flex size-10 flex-none items-center justify-center rounded-full text-xl font-bold ${ok ? 'bg-[#1B7A34] text-white' : 'bg-[#E4E9E8]'}`}>
          {ok ? '✓' : '!'}
        </span>
        <span className="flex flex-1 flex-col">
          <span className="font-bold">{label}</span>
          <span className="text-base">{ok ? t('elderSetup.ok') : t('elderSetup.notOk')}</span>
        </span>
      </div>
      {!ok && action && (
        <button type="button" onClick={action.run}
          className="min-h-14 rounded-xl bg-teal-dark text-lg font-semibold text-white">
          {action.text}
        </button>
      )}
      {children}
    </li>
  );
}

function SmallButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" onClick={onClick}
      className="min-h-14 rounded-xl border-2 border-teal-dark text-lg font-semibold text-teal-dark">
      {children}
    </button>
  );
}
