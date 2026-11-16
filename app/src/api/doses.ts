import { useQuery } from '@tanstack/react-query';
import { api } from './client';
import type { Food, PillColour, Shape } from './medicines';

export interface DoseDetail {
  id: string;
  personId: string;
  personName: string;
  personPhone?: string;
  medicineId?: string;
  medicineName: string;
  voiceName?: string;
  shape?: Shape;
  color?: PillColour;
  hasPhoto: boolean;
  photoVersion?: string;
  food?: Food;
  date: string;
  time: string;
  status: 'TAKEN' | 'MISSED';
  source: 'PHONE' | 'FAMILY' | 'SERVER';
  loggedAt: string;
  /** How many times the alarm rang for this dose. */
  rings?: number;
  phoneLinked: boolean;
  /** Push is set up on the server, so "Play the reminder again" can work. */
  pushEnabled: boolean;
}

export interface DaySummary {
  date: string;
  total: number;
  taken: number;
  missed: number;
}

export const doseKeys = {
  alerts: ['dose-alerts'] as const,
  one: (id: string) => ['dose', id] as const,
  week: (personId: string) => ['week', personId] as const,
};

/** Missed doses from today and yesterday. Checked every minute so a new alert shows without a reload. */
export const useAlerts = () =>
  useQuery({
    queryKey: doseKeys.alerts,
    queryFn: () => api<DoseDetail[]>('/api/dose-logs/alerts'),
    refetchInterval: 60_000,
  });

export const useDose = (id: string) =>
  useQuery({ queryKey: doseKeys.one(id), queryFn: () => api<DoseDetail>(`/api/dose-logs/${id}`) });

export const useWeek = (personId: string | undefined) =>
  useQuery({
    queryKey: doseKeys.week(personId ?? ''),
    queryFn: () => api<DaySummary[]>(`/api/people/${personId}/week`),
    enabled: !!personId,
  });
