/**
 * What the elder's phone remembers after pairing. Kept in localStorage for the web build;
 * the Android build (Phase 4) also hands this to the native alarm plugin.
 */
export interface PairedPerson {
  id: string;
  name: string;
  voiceLanguage: string;
  timeZone: string;
}

export interface PairedDevice {
  token: string;
  deviceId: string;
  familyName: string;
  person: PairedPerson;
}

const KEY = 'mr.device';

export const deviceStore = {
  get(): PairedDevice | null {
    try {
      const raw = localStorage.getItem(KEY);
      return raw ? (JSON.parse(raw) as PairedDevice) : null;
    } catch {
      return null;
    }
  },
  set(device: PairedDevice | null) {
    try {
      if (device) localStorage.setItem(KEY, JSON.stringify(device));
      else localStorage.removeItem(KEY);
    } catch {
      /* storage blocked: the phone will ask for a code again next time */
    }
  },
};
