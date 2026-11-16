import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { alertsState, turnOnAlerts, type AlertsState } from '../push/webPush';
import { Button } from './ui';

/** "Get missed-dose alerts on this phone". Hidden once alerts are on, or when push is not set up. */
export function AlertsCard() {
  const { t } = useTranslation();
  const [state, setState] = useState<AlertsState>(() => alertsState());
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  if (state === 'on' || state === 'unconfigured' || state === 'unsupported') return null;

  async function enable() {
    setBusy(true);
    setFailed(false);
    try {
      setState(await turnOnAlerts());
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="alerts-heading" className="flex flex-col gap-2 rounded-[18px] border border-[#C9D3D2] bg-white p-4">
      <h2 id="alerts-heading" className="font-bold">{t('alerts.title')}</h2>
      {state === 'needsHomeScreen' && <p className="text-sm text-muted">{t('alerts.iosHint')}</p>}
      {state === 'blocked' && <p className="text-sm text-danger">{t('alerts.blocked')}</p>}
      {state === 'off' && (
        <>
          <p className="text-sm text-muted">{t('alerts.body')}</p>
          <Button type="button" onClick={enable} disabled={busy}>{t('alerts.enable')}</Button>
        </>
      )}
      {failed && <p role="alert" className="text-sm text-danger">{t('errors.generic')}</p>}
    </section>
  );
}
