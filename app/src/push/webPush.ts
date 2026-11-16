import { api } from '../api/client';

/**
 * Missed-dose alerts on a family member's own phone or computer (web push via Firebase).
 * Needs the VITE_FIREBASE_* settings (see app/.env.example). Works in Chrome/Edge/Firefox, and in Safari on
 * iPhone (iOS 16.4+) only after "Add to Home Screen". The Firebase SDK is loaded only when alerts are turned on.
 */
const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY as string | undefined,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID as string | undefined,
  messagingSenderId: import.meta.env.VITE_FIREBASE_SENDER_ID as string | undefined,
  appId: import.meta.env.VITE_FIREBASE_APP_ID as string | undefined,
};
const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY as string | undefined;
const TOKEN_KEY = 'mr.pushToken';

export type AlertsState = 'unconfigured' | 'unsupported' | 'needsHomeScreen' | 'off' | 'on' | 'blocked';

const isIos = () => /iPad|iPhone|iPod/.test(navigator.userAgent);
const isStandalone = () =>
  window.matchMedia?.('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true;

export function alertsState(): AlertsState {
  if (!config.apiKey || !config.appId || !vapidKey) return 'unconfigured';
  if (isIos() && !isStandalone()) return 'needsHomeScreen';
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) return 'unsupported';
  if (Notification.permission === 'denied') return 'blocked';
  return Notification.permission === 'granted' && savedToken() ? 'on' : 'off';
}

function savedToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

/** Asks permission (must be called from a tap), gets a push token, and registers it with our server. */
export async function turnOnAlerts(): Promise<AlertsState> {
  if ((await Notification.requestPermission()) !== 'granted') return alertsState();
  const registration = await navigator.serviceWorker.register('/sw.js');
  const [{ initializeApp, getApps }, { getMessaging, getToken }] = await Promise.all([
    import('firebase/app'),
    import('firebase/messaging'),
  ]);
  const app = getApps()[0] ?? initializeApp(config);
  const token = await getToken(getMessaging(app), { vapidKey, serviceWorkerRegistration: registration });
  await api('/api/me/push-tokens', { method: 'POST', body: JSON.stringify({ token }) });
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    /* ignore */
  }
  return 'on';
}

/** On logout: stop alerts for this account on this device. */
export async function forgetAlerts() {
  const token = savedToken();
  if (!token) return;
  try {
    localStorage.removeItem(TOKEN_KEY);
    await api('/api/me/push-tokens', { method: 'DELETE', body: JSON.stringify({ token }) });
  } catch {
    /* best effort: the server also drops tokens Firebase reports as invalid */
  }
}
