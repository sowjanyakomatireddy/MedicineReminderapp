import { Capacitor, registerPlugin, type PluginListenerHandle } from '@capacitor/core';

/** True inside the Android app (Capacitor); false in a browser. */
export const isNativeApp = () => Capacitor.isNativePlatform();

export type SettingsTarget = 'notifications' | 'exactAlarm' | 'fullScreen' | 'battery' | 'autostart' | 'lockScreen' | 'app';
export type VoiceStatus = 'available' | 'missingData' | 'notSupported' | 'noEngine';

export interface PhoneStatus {
  manufacturer: string;
  model: string;
  sdk: number;
  notifications: boolean;
  exactAlarms: boolean;
  fullScreen: boolean;
  batteryUnrestricted: boolean;
  /** A maker-specific autostart page exists (Xiaomi, Oppo, Vivo, Samsung, Huawei, OnePlus). */
  autostartPage: boolean;
  /** Xiaomi's separate "Show on lock screen" permission page exists. */
  xiaomiLockScreenPage: boolean;
  notificationPermission?: 'granted' | 'denied' | 'prompt' | 'prompt-with-rationale';
}

export interface AlarmChip {
  text: string;
  tone: 'before' | 'after' | 'grey';
}

/** One alarm as the native side stores it (see AlarmStore.java). */
export interface NativeAlarm {
  id: string;
  kind: 'MEDICINE' | 'ROUTINE';
  time: string;
  /** Monday = 1 ... Sunday = 7 */
  days: number[];
  remindBeforeMin: number;
  repeatEveryMin: number;
  stopAfterMin: number;
  title: string;
  subtitle?: string;
  chips?: AlarmChip[];
  spoken: string;
  shape?: string;
  color?: string;
  photoUri?: string;
  voiceNoteUri?: string;
}

/** Words on the native alarm and Taken screens, in the person's language. */
export interface AlarmUi {
  takenTitle: string;
  /** "{{name}} వేసుకున్నారు" — the native side fills in the medicine. */
  takenLine: string;
  nextMedicine: string;
  familyInformed: string;
  familyLater: string;
  ok: string;
  medicineTime: string;
  routineTime: string;
  taken: string;
  takenDone: string;
  listenAgain: string;
  voiceSays: string;
  morning: string;
  afternoon: string;
  evening: string;
  night: string;
}

/**
 * Raw sentence parts in the person's language, so the native side can build alarms itself after a
 * background SYNC push (android/.../AlarmBuilder.java).
 */
export interface AlarmTemplates {
  /** e.g. "{{person}}, ఇది {{voiceName}}. {{foodPhrase}}." */
  sentence: string;
  foodPhrase: Record<string, string>;
  food: Record<string, string>;
  shape: Record<string, string>;
  colour: Record<string, string>;
}

export interface AlarmProfile {
  timeZone: string;
  lang: string;
  ui: AlarmUi;
  templates: AlarmTemplates;
}

/** What GET /api/devices/me/sync returns (kept on the phone for offline use). */
export interface SyncData {
  person: { id: string; name: string; voiceLanguage: string; timeZone: string; familyPhone?: string };
  familyName: string;
  medicines: import('../api/medicines').Medicine[];
  logs: { scheduleId: string; date: string; status: 'TAKEN' | 'MISSED' }[];
  serverTime: string;
  pushEnabled: boolean;
}

export interface StoredSync {
  sync?: SyncData;
  syncedAt?: number;
  /** "scheduleId@yyyy-MM-dd" → TAKEN / MISSED, as the phone knows it (including not yet uploaded). */
  statuses?: Record<string, 'TAKEN' | 'MISSED'>;
  pending?: number;
}

export interface AlarmConfig {
  timeZone: string;
  /** BCP-47 voice, e.g. te-IN */
  lang: string;
  ui: AlarmUi;
  alarms: NativeAlarm[];
}

export interface ScheduledAlarm {
  id: string;
  title: string;
  next: number | null;
  type: 'RING' | 'EXPIRE' | null;
  test: boolean;
}

export interface DoseLog {
  id: string;
  scheduleId: string;
  date: string;
  status: 'TAKEN' | 'MISSED';
  at: number;
}

export interface MedAlarmPlugin {
  setSchedule(config: AlarmConfig): Promise<{ alarms: ScheduledAlarm[] }>;
  /** Who the phone belongs to, and every word the alarm needs, without changing the alarm list. */
  setProfile(options: AlarmProfile): Promise<void>;
  /** After pairing: lets the native side sync and upload by itself (also when the app is closed). */
  setDevice(options: { apiBase: string; token: string }): Promise<void>;
  /** Fetch the schedule now. Rejects with code UNLINKED or OFFLINE. */
  syncNow(): Promise<StoredSync>;
  /** The last sync stored on the phone, with local statuses (works offline). */
  getSyncData(): Promise<StoredSync>;
  getScheduled(): Promise<{ alarms: ScheduledAlarm[] }>;
  testAlarm(options: Partial<NativeAlarm> & { seconds?: number }): Promise<{ alarms: ScheduledAlarm[] }>;
  cancelAll(): Promise<void>;
  getPendingLogs(): Promise<{ logs: DoseLog[] }>;
  ackLogs(options: { ids: string[] }): Promise<void>;
  getStatus(): Promise<PhoneStatus>;
  requestNotifications(): Promise<PhoneStatus>;
  openSettings(options: { target: SettingsTarget }): Promise<{ opened: boolean }>;
  checkVoice(options: { lang: string }): Promise<{ status: VoiceStatus; engine?: string }>;
  installVoice(): Promise<{ opened: boolean }>;
  speak(options: { text: string; lang: string }): Promise<void>;
  addListener(event: 'doseLogged', fn: (log: DoseLog) => void): Promise<PluginListenerHandle>;
  addListener(event: 'synced', fn: () => void): Promise<PluginListenerHandle>;
}

/** The Java plugin in android/app/src/main/java/app/medreminder/alarm/MedAlarmPlugin.java. */
export const MedAlarm = registerPlugin<MedAlarmPlugin>('MedAlarm');
