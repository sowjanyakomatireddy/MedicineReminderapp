import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import i18n from '../i18n';
import { tokenStore } from '../api/client';
import { mockApi, renderAt } from '../test-utils';
import { spokenSentence } from '../voice/sentence';
import { FamilyHomePage } from './FamilyHomePage';
import { MedicineFormPage } from './MedicineFormPage';

const me = { id: 'u1', email: 'a@b.com', displayName: 'Sowjanya', appLanguage: 'en' };
const family = { id: 'f1', name: 'Reddy family', myRole: 'EDITOR', myMemberId: 'm1' };
const thatha = {
  id: 'p1', name: 'Thatha', voiceLanguage: 'te', timeZone: 'Asia/Kolkata',
  linkedPhones: 1, medicineCount: 0, hasPhoto: false,
};

beforeEach(async () => {
  await i18n.changeLanguage('en');
  tokenStore.set('user-token');
});
afterEach(() => {
  vi.restoreAllMocks();
  tokenStore.set(null);
});

describe('spoken sentence', () => {
  it('uses each language’s own template and food phrase', () => {
    const input = { person: 'తాతయ్య', voiceName: 'బీపీ మాత్ర', food: 'BEFORE' as const };
    expect(spokenSentence('te', input)).toBe('తాతయ్య, ఇది బీపీ మాత్ర. భోజనానికి ముందు వేసుకోండి.');
    expect(spokenSentence('hi', { ...input, voiceName: 'बीपी की गोली' })).toBe('తాతయ్య, यह बीपी की गोली है। खाने से पहले लें।');
    expect(spokenSentence('en', { person: 'Thatha', voiceName: 'BP tablet', food: 'AFTER' }))
      .toBe('Thatha, this is your BP tablet. Take it after food.');
  });
});

describe('Add a medicine', () => {
  function setup() {
    return mockApi({
      'GET /api/auth/me': () => ({ body: me }),
      'GET /api/families/me': () => ({ body: family }),
      'GET /api/people': () => ({ body: [thatha] }),
      'POST /api/medicines': () => ({ status: 201, body: { id: 'med1', personId: 'p1', schedules: [] } }),
      'PUT /api/people/p1': () => ({ body: thatha }),
      'GET /api/families/me/today': () => ({ body: [] }),
    });
  }

  it('previews the Telugu sentence and follows the food choice', async () => {
    setup();
    renderAt('/medicines/new?person=p1', [{ path: '/medicines/new', element: <MedicineFormPage /> }]);

    expect(await screen.findByText('For Thatha')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Medicine name'), { target: { value: 'Amlodipine 5 mg' } });
    fireEvent.change(screen.getByLabelText('What the voice calls it'), { target: { value: 'బీపీ మాత్ర' } });
    const preview = () => screen.getByText(/Voice preview:/).querySelector('q')!;
    expect(preview()).toHaveTextContent('Thatha, ఇది బీపీ మాత్ర. భోజనానికి ముందు వేసుకోండి.');
    expect(preview()).toHaveAttribute('lang', 'te');

    fireEvent.click(screen.getByLabelText(/After food/));
    expect(preview()).toHaveTextContent('భోజనం తర్వాత వేసుకోండి');
  });

  it('saves the medicine with its times and alarm settings', async () => {
    const calls = setup();
    renderAt('/medicines/new?person=p1', [
      { path: '/medicines/new', element: <MedicineFormPage /> },
      { path: '/', element: <p>home</p> },
    ]);
    fireEvent.change(await screen.findByLabelText('Medicine name'), { target: { value: 'Amlodipine 5 mg' } });
    fireEvent.change(screen.getByLabelText('First reminder'), { target: { value: '10' } });
    fireEvent.click(screen.getByLabelText('Round'));
    fireEvent.click(screen.getByLabelText('White'));

    // Second time: night, Mondays only.
    fireEvent.click(screen.getByRole('button', { name: '+ Add another time' }));
    const second = screen.getByRole('group', { name: 'Time 2' });
    fireEvent.change(within(second).getByLabelText('Repeat'), { target: { value: 'some' } });
    fireEvent.click(within(second).getByLabelText('Monday'));

    fireEvent.click(screen.getByRole('button', { name: 'Save and send to Thatha’s phone' }));
    await screen.findByText('home');

    const body = calls.find((c) => c.key === 'POST /api/medicines')!.body as Record<string, unknown>;
    expect(body).toMatchObject({ personId: 'p1', name: 'Amlodipine 5 mg', shape: 'ROUND', color: 'WHITE' });
    expect(body.schedules).toEqual([
      expect.objectContaining({ time: '07:00', food: 'BEFORE', remindBeforeMin: 10, repeatEveryMin: 5, stopAfterMin: 30 }),
      expect.objectContaining({ time: '21:00', food: 'AFTER', days: ['MONDAY'], remindBeforeMin: 10 }),
    ]);
    expect((body.schedules as { days: string[] }[])[0].days).toHaveLength(7);
    // Voice language unchanged, so the person was not updated.
    expect(calls.some((c) => c.key === 'PUT /api/people/p1')).toBe(false);
  });

  it('changing the voice language updates the person and says so', async () => {
    const calls = setup();
    renderAt('/medicines/new?person=p1', [
      { path: '/medicines/new', element: <MedicineFormPage /> },
      { path: '/', element: <p>home</p> },
    ]);
    fireEvent.change(await screen.findByLabelText('Medicine name'), { target: { value: 'Metformin' } });
    fireEvent.click(screen.getByLabelText('தமிழ்'));
    expect(screen.getByText('This changes the voice for all of Thatha’s reminders.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Save and send/ }));
    await screen.findByText('home');
    expect(calls.find((c) => c.key === 'PUT /api/people/p1')?.body).toMatchObject({ voiceLanguage: 'ta' });
  });

  it('will not save a time with no days chosen', async () => {
    setup();
    renderAt('/medicines/new?person=p1', [{ path: '/medicines/new', element: <MedicineFormPage /> }]);
    fireEvent.change(await screen.findByLabelText('Medicine name'), { target: { value: 'X' } });
    fireEvent.change(screen.getByLabelText('Repeat'), { target: { value: 'some' } });
    expect(screen.getByText('Choose at least one day.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Save and send/ })).toBeDisabled();
  });
});

describe('Family Home', () => {
  it('shows today for the chosen person with written statuses', async () => {
    mockApi({
      'GET /api/auth/me': () => ({ body: me }),
      'GET /api/families/me': () => ({ body: family }),
      'GET /api/people': () => ({ body: [thatha, { ...thatha, id: 'p2', name: 'Amma' }] }),
      'GET /api/families/me/today': () => ({
        body: [
          {
            personId: 'p1', name: 'Thatha', voiceLanguage: 'te', date: '2026-11-16', total: 2, taken: 0,
            doses: [
              { scheduleId: 's1', medicineId: 'm1', name: 'Amlodipine 5 mg', voiceName: 'బీపీ మాత్ర', shape: 'ROUND',
                color: 'WHITE', hasPhoto: false, time: '07:00', alertAt: '06:55', food: 'BEFORE', status: 'UNCONFIRMED' },
              { scheduleId: 's2', medicineId: 'm2', name: 'Sugar tablet', hasPhoto: false, time: '13:30',
                alertAt: '13:25', food: 'AFTER', status: 'DUE' },
            ],
          },
          { personId: 'p2', name: 'Amma', voiceLanguage: 'te', date: '2026-11-16', total: 0, taken: 0, doses: [] },
        ],
      }),
    });
    renderAt('/', [{ path: '/', element: <FamilyHomePage /> }]);

    expect(await screen.findByRole('heading', { name: 'Today · Thatha · Telugu' })).toBeInTheDocument();
    expect(screen.getByText('0 of 2')).toBeInTheDocument();
    const bp = screen.getByText('బీపీ మాత్ర').closest('li')!;
    expect(within(bp).getByText(/Before food · alert/)).toBeInTheDocument();
    expect(within(bp).getByText('Not confirmed yet')).toBeInTheDocument();
    expect(screen.getByText('Ringing now')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thatha' })).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(screen.getByRole('button', { name: 'Amma' }));
    await waitFor(() => expect(screen.getByText('No medicines yet for Amma.')).toBeInTheDocument());
    expect(screen.getByRole('link', { name: 'Add a medicine' })).toHaveAttribute('href', '/medicines/new?person=p2');
  });
});
