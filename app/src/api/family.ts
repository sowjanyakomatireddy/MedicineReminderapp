import { useQuery } from '@tanstack/react-query';
import { api, ApiError } from './client';

export type Role = 'EDITOR' | 'VIEWER';
export type PairingPurpose = 'DEVICE' | 'MEMBER';

export interface Family {
  id: string;
  name: string;
  myRole: Role;
  myMemberId: string;
}

export interface Member {
  id: string;
  userId: string;
  displayName: string;
  email: string;
  role: Role;
  me: boolean;
}

export interface Person {
  id: string;
  name: string;
  voiceLanguage: string;
  timeZone: string;
  linkedPhones: number;
  medicineCount: number;
  hasPhoto: boolean;
  photoVersion?: string;
  phone?: string;
  familyPhone?: string;
}

export interface PersonInput {
  name: string;
  voiceLanguage: string;
  timeZone: string;
  phone?: string;
  familyPhone?: string;
}

export interface Device {
  id: string;
  personId: string;
  name?: string;
  platform: string;
  createdAt: string;
  lastSyncAt?: string;
}

export interface PairingCode {
  code: string;
  purpose: PairingPurpose;
  role?: Role;
  expiresAt: string;
}

export const keys = {
  family: ['family'] as const,
  members: ['members'] as const,
  people: ['people'] as const,
  person: (id: string) => ['people', id] as const,
  devices: (personId: string) => ['devices', personId] as const,
};

/** The caller's family, or null if they have not created or joined one yet. */
export function useFamily() {
  return useQuery({
    queryKey: keys.family,
    queryFn: async () => {
      try {
        return await api<Family>('/api/families/me');
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null;
        throw e;
      }
    },
  });
}

export const useMembers = () =>
  useQuery({ queryKey: keys.members, queryFn: () => api<Member[]>('/api/families/me/members') });

export const usePeople = () => useQuery({ queryKey: keys.people, queryFn: () => api<Person[]>('/api/people') });

export const usePerson = (id: string | undefined) =>
  useQuery({ queryKey: keys.person(id ?? ''), queryFn: () => api<Person>(`/api/people/${id}`), enabled: !!id });

export const useDevices = (personId: string) =>
  useQuery({ queryKey: keys.devices(personId), queryFn: () => api<Device[]>(`/api/devices?personId=${personId}`) });

export const createCode = (purpose: PairingPurpose, role?: Role) =>
  api<PairingCode>('/api/pairing/codes', { method: 'POST', body: JSON.stringify({ purpose, role }) });
