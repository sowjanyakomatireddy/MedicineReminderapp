import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import i18n from '../i18n';
import { tokenStore } from '../api/client';
import { mockApi, renderAt } from '../test-utils';
import { RoutinePage } from './RoutinePage';

const me = { id: 'u1', email: 'a@b.com', displayName: 'Sowjanya', appLanguage: 'en' };
const family = { id: 'f1', name: 'Reddy family', myRole: 'EDITOR', myMemberId: 'm1' };
const person = (id: string, name: string, periodTracking: boolean) => ({
  id, name, voiceLanguage: 'te', timeZone: 'Asia/Kolkata', linkedPhones: 1, medicineCount: 0, hasPhoto: false, periodTracking,
});
const people = [person('p1', 'తాతయ్య', false), person('p2', 'అమ్మ', true)];
const water = { id: 'r1', personId: 'p2', time: '06:30', days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'], label: 'Drink a glass of water', spokenText: 'నీళ్ళు తాగండి', active: true };
const period = {
  enabled: true, cycleLengthDays: 28, averageCycleDays: 29, periodLengthDays: 5, remindDaysBefore: 2, remindTime: '09:00',
  today: '2026-11-16', lastStart: '2026-10-22', nextExpected: '2026-11-20', daysUntil: 4, reminderDate: '2026-11-18',
  recentStarts: ['2026-10-22', '2026-09-23'],
};

beforeEach(async () => {
  await i18n.changeLanguage('en');
  tokenStore.set('user-token');
});
afterEach(() => {
  vi.restoreAllMocks();
  tokenStore.set(null);
});

function setup() {
  return mockApi({
    'GET /api/auth/me': () => ({ body: me }),
    'GET /api/families/me': () => ({ body: family }),
    'GET /api/people': () => ({ body: people }),
    'GET /api/people/p1/routine': () => ({ body: [] }),
    'GET /api/people/p2/routine': () => ({ body: [water] }),
    'GET /api/people/p2/period': () => ({ body: period }),
    'POST /api/people/p2/period/starts': () => ({ body: { ...period, lastStart: '2026-11-16', nextExpected: '2026-12-15', daysUntil: 29 } }),
    'POST /api/people/p1/routine': () => ({ status: 201, body: {} }),
  });
}

describe('Daily routine', () => {
  it('shows the period card only for a person with period reminders on', async () => {
    setup();
    renderAt('/routine?person=p1', [{ path: '/routine', element: <RoutinePage /> }]);
    expect(await screen.findByRole('heading', { name: 'తాతయ్య’s day' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Next period expected' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'అమ్మ' }));
    expect(await screen.findByRole('heading', { name: 'Next period expected' })).toBeInTheDocument();
    expect(screen.getByText('In 4 days')).toBeInTheDocument();
    expect(screen.getByText('Dates are estimates and not medical advice.')).toBeInTheDocument();
    // The strip says what each day is in words, not only colour.
    expect(screen.getByText(/November 20, expected start/)).toBeInTheDocument();
    expect(screen.getByText(/November 16, today/)).toBeInTheDocument();
    // What the voice will say, in her language.
    expect(screen.getByText('అమ్మ, పీరియడ్స్ తేదీ దగ్గరలో ఉంది.')).toBeInTheDocument();
    // Her routine, with the spoken words.
    expect(await screen.findByText('Drink a glass of water')).toBeInTheDocument();
    expect(screen.getByText('“నీళ్ళు తాగండి”')).toBeInTheDocument();
  });

  it('records "Period started today"', async () => {
    const calls = setup();
    renderAt('/routine?person=p2', [{ path: '/routine', element: <RoutinePage /> }]);
    fireEvent.click(await screen.findByRole('button', { name: 'Period started today' }));
    expect(await screen.findByText('In 29 days')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'POST /api/people/p2/period/starts')?.body).toEqual({});
  });

  it('adds a routine reminder with a spoken Telugu preview', async () => {
    const calls = setup();
    renderAt('/routine?person=p1', [{ path: '/routine', element: <RoutinePage /> }]);
    fireEvent.click(await screen.findByRole('button', { name: '+ Add' }));
    const form = screen.getByRole('form', { name: 'Add a routine reminder' });
    fireEvent.change(within(form).getByLabelText('What it is (for the family)'), { target: { value: 'Evening walk' } });
    expect(within(form).getByText('తాతయ్య, Evening walk.')).toBeInTheDocument();
    fireEvent.change(within(form).getByLabelText(/What the voice says, in తెలుగు/), { target: { value: 'కొంచెం నడవండి' } });
    expect(within(form).getByText('తాతయ్య, కొంచెం నడవండి.')).toBeInTheDocument();
    fireEvent.change(within(form).getByLabelText('Time'), { target: { value: '17:00' } });
    fireEvent.click(within(form).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(calls.some((c) => c.key === 'POST /api/people/p1/routine')).toBe(true));
    expect(calls.find((c) => c.key === 'POST /api/people/p1/routine')?.body).toMatchObject({
      time: '17:00', label: 'Evening walk', spokenText: 'కొంచెం నడవండి',
    });
  });
});
