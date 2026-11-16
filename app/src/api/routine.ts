import { useQuery } from '@tanstack/react-query';
import { api } from './client';
import type { Day } from './medicines';

export interface RoutineItem {
  id: string;
  personId: string;
  time: string;
  days: Day[];
  /** What the family sees. */
  label: string;
  /** What the voice says, in the person's language (the label is read if empty). */
  spokenText?: string;
  active: boolean;
}

export interface RoutineInput {
  time: string;
  days: Day[];
  label: string;
  spokenText?: string;
  active?: boolean;
}

export interface Period {
  enabled: boolean;
  cycleLengthDays: number;
  averageCycleDays: number;
  periodLengthDays: number;
  remindDaysBefore: number;
  remindTime: string;
  today: string;
  lastStart?: string;
  nextExpected?: string;
  daysUntil?: number;
  reminderDate?: string;
  recentStarts: string[];
}

export interface PeriodSettings {
  enabled: boolean;
  cycleLengthDays: number;
  periodLengthDays: number;
  remindDaysBefore: number;
  remindTime: string;
}

export const routineKeys = {
  list: (personId: string) => ['routine', personId] as const,
  period: (personId: string) => ['period', personId] as const,
};

export const useRoutine = (personId: string | undefined) =>
  useQuery({
    queryKey: routineKeys.list(personId ?? ''),
    queryFn: () => api<RoutineItem[]>(`/api/people/${personId}/routine`),
    enabled: !!personId,
  });

export const usePeriod = (personId: string | undefined, enabled = true) =>
  useQuery({
    queryKey: routineKeys.period(personId ?? ''),
    queryFn: () => api<Period>(`/api/people/${personId}/period`),
    enabled: !!personId && enabled,
  });
