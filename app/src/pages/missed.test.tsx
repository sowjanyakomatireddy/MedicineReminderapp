import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import i18n from '../i18n';
import { tokenStore } from '../api/client';
import { mockApi, renderAt } from '../test-utils';
import { MissedAlertPage } from './MissedAlertPage';
import { FamilyHomePage } from './FamilyHomePage';

const me = { id: 'u1', email: 'a@b.com', displayName: 'Sowjanya', appLanguage: 'en' };
const family = { id: 'f1', name: 'Reddy family', myRole: 'EDITOR', myMemberId: 'm1' };
const missed = {
  id: 'd1', personId: 'p1', personName: 'Thatha', personPhone: '+91 90000 00000', medicineId: 'm2',
  medicineName: 'Sugar tablet', hasPhoto: false, food: 'AFTER', date: '2026-11-16', time: '13:30',
  status: 'MISSED', source: 'SERVER', loggedAt: '2026-11-16T08:32:00Z', rings: 7, phoneLinked: true, pushEnabled: true,
};
const week = Array.from({ length: 7 }, (_, i) => ({
  date: `2026-11-${String(10 + i).padStart(2, '0')}`, total: 3, taken: i === 6 ? 1 : 3, missed: i === 6 ? 1 : 0,
}));

beforeEach(async () => {
  await i18n.changeLanguage('en');
  tokenStore.set('user-token');
});
afterEach(() => {
  vi.restoreAllMocks();
  tokenStore.set(null);
});

describe('Missed alert screen', () => {
  const routes = (replayStatus = 202) => ({
    'GET /api/auth/me': () => ({ body: me }),
    'GET /api/families/me': () => ({ body: family }),
    'GET /api/dose-logs/d1': () => ({ body: missed }),
    'GET /api/people/p1/week': () => ({ body: week }),
    'POST /api/dose-logs/d1/replay': () => ({ status: replayStatus, body: replayStatus === 202 ? {} : { status: replayStatus } }),
    'POST /api/dose-logs/d1/taken': () => ({ body: { ...missed, status: 'TAKEN', source: 'FAMILY' } }),
    'GET /api/dose-logs/alerts': () => ({ body: [] }),
    'GET /api/families/me/today': () => ({ body: [] }),
    'GET /api/people': () => ({ body: [] }),
  });

  it('shows the dose, how often it rang, and a real call link', async () => {
    mockApi(routes());
    renderAt('/missed/d1', [{ path: '/missed/:id', element: <MissedAlertPage /> }]);
    expect(await screen.findByRole('heading', { name: 'Thatha has not taken the 1:30 PM medicine' })).toBeInTheDocument();
    expect(screen.getByText('Missed · alarm rang 7 times')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Call Thatha' })).toHaveAttribute('href', 'tel:+919000000000');
    expect(await screen.findByText('Today: 1 of 3 taken')).toBeInTheDocument();
    // The week strip does not rely on colour: each day has a written label.
    expect(screen.getAllByText(/: All taken$/)).toHaveLength(6);
    expect(screen.getByText(/: 1 missed$/)).toBeInTheDocument();
  });

  it('replays the reminder, and explains when push is not set up', async () => {
    mockApi(routes(202));
    renderAt('/missed/d1', [{ path: '/missed/:id', element: <MissedAlertPage /> }]);
    fireEvent.click(await screen.findByRole('button', { name: 'Play the reminder again' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Ringing on Thatha’s phone now.');

    vi.restoreAllMocks();
    mockApi(routes(503));
    fireEvent.click(screen.getByRole('button', { name: 'Play the reminder again' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Push messages are not set up yet');
  });

  it('marks it taken and goes home', async () => {
    const calls = mockApi(routes());
    renderAt('/missed/d1', [
      { path: '/missed/:id', element: <MissedAlertPage /> },
      { path: '/', element: <p>home</p> },
    ]);
    fireEvent.click(await screen.findByRole('button', { name: 'Already taken? Mark as taken' }));
    expect(await screen.findByText('home')).toBeInTheDocument();
    expect(calls.some((c) => c.key === 'POST /api/dose-logs/d1/taken')).toBe(true);
  });
});

describe('Family Home alerts', () => {
  it('shows a missed dose at the top and as a status that opens it', async () => {
    mockApi({
      'GET /api/auth/me': () => ({ body: me }),
      'GET /api/families/me': () => ({ body: family }),
      'GET /api/people': () => ({ body: [{ id: 'p1', name: 'Thatha', voiceLanguage: 'te', timeZone: 'Asia/Kolkata', linkedPhones: 1, medicineCount: 1, hasPhoto: false }] }),
      'GET /api/dose-logs/alerts': () => ({ body: [missed] }),
      'GET /api/families/me/today': () => ({
        body: [{ personId: 'p1', name: 'Thatha', voiceLanguage: 'te', date: '2026-11-16', total: 2, taken: 1, doses: [
          { scheduleId: 's1', medicineId: 'm1', name: 'BP', hasPhoto: false, time: '07:00', alertAt: '06:55', food: 'BEFORE', status: 'TAKEN', doseLogId: 'd0' },
          { scheduleId: 's2', medicineId: 'm2', name: 'Sugar tablet', hasPhoto: false, time: '13:30', alertAt: '13:25', food: 'AFTER', status: 'MISSED', doseLogId: 'd1' },
        ] }],
      }),
    });
    renderAt('/', [{ path: '/', element: <FamilyHomePage /> }]);
    const banner = await screen.findByRole('link', { name: /Thatha has not taken the 1:30 PM medicine/ });
    expect(banner).toHaveAttribute('href', '/missed/d1');
    expect(await screen.findByText('1 of 2')).toBeInTheDocument();
    const sugar = screen.getByText('Sugar tablet', { selector: 'span.text-\\[17px\\]' }).closest('li')!;
    expect(within(sugar).getByRole('link', { name: '! Missed' })).toHaveAttribute('href', '/missed/d1');
    expect(screen.getByText('✓ Taken')).toBeInTheDocument();
  });
});
