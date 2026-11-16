import { useQuery } from '@tanstack/react-query';
import { api } from './client';

export type Food = 'BEFORE' | 'AFTER' | 'ANY';
export type Shape = 'ROUND' | 'OVAL' | 'CAPSULE' | 'OTHER';
export type PillColour = 'WHITE' | 'YELLOW' | 'ORANGE' | 'PINK' | 'RED' | 'BLUE' | 'GREEN' | 'BROWN' | 'MIXED';
export type Day = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export const DAYS: readonly Day[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export const FOODS: readonly Food[] = ['BEFORE', 'AFTER', 'ANY'];
export const SHAPES: readonly Shape[] = ['ROUND', 'OVAL', 'CAPSULE', 'OTHER'];
export const COLOURS: readonly PillColour[] = ['WHITE', 'YELLOW', 'ORANGE', 'PINK', 'RED', 'BLUE', 'GREEN', 'BROWN', 'MIXED'];

export interface Schedule {
  id?: string;
  /** "HH:mm", local time in the person's time zone. */
  time: string;
  days: Day[];
  food: Food;
  remindBeforeMin: number;
  repeatEveryMin: number;
  stopAfterMin: number;
  active: boolean;
}

export interface Medicine {
  id: string;
  personId: string;
  name: string;
  voiceName?: string;
  shape?: Shape;
  color?: PillColour;
  notes?: string;
  hasPhoto: boolean;
  photoVersion?: string;
  schedules: Schedule[];
  updatedAt: string;
}

export interface MedicineInput {
  personId?: string;
  name: string;
  voiceName?: string;
  shape?: Shape;
  color?: PillColour;
  notes?: string;
  schedules: Schedule[];
}

export type DoseStatus = 'UPCOMING' | 'DUE' | 'UNCONFIRMED' | 'TAKEN' | 'MISSED';

export interface Dose {
  scheduleId: string;
  medicineId: string;
  name: string;
  voiceName?: string;
  shape?: Shape;
  color?: PillColour;
  hasPhoto: boolean;
  photoVersion?: string;
  time: string;
  alertAt: string;
  food: Food;
  status: DoseStatus;
  /** Set once the dose is answered (TAKEN / MISSED); opens the Missed alert screen. */
  doseLogId?: string;
}

export interface PersonToday {
  personId: string;
  name: string;
  voiceLanguage: string;
  date: string;
  total: number;
  taken: number;
  doses: Dose[];
}

export const medicineKeys = {
  all: ['medicines'] as const,
  forPerson: (personId: string) => ['medicines', 'person', personId] as const,
  one: (id: string) => ['medicines', id] as const,
  today: ['today'] as const,
};

export const useMedicines = (personId: string | undefined) =>
  useQuery({
    queryKey: medicineKeys.forPerson(personId ?? ''),
    queryFn: () => api<Medicine[]>(`/api/medicines?personId=${personId}`),
    enabled: !!personId,
  });

export const useMedicine = (id: string | undefined) =>
  useQuery({ queryKey: medicineKeys.one(id ?? ''), queryFn: () => api<Medicine>(`/api/medicines/${id}`), enabled: !!id });

/** Refreshes every minute so "Ringing now" and the other statuses stay current. */
export const useToday = () =>
  useQuery({
    queryKey: medicineKeys.today,
    queryFn: () => api<PersonToday[]>('/api/families/me/today'),
    refetchInterval: 60_000,
  });
