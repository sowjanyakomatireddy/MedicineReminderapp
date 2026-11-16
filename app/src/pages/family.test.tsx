import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import i18n from '../i18n';
import { tokenStore } from '../api/client';
import { mockApi, renderAt } from '../test-utils';
import { FamilyHomePage } from './FamilyHomePage';
import { MembersPage } from './MembersPage';
import { SetupPhonePage } from './SetupPhonePage';

const me = { id: 'u1', email: 'a@b.com', displayName: 'Sowjanya', appLanguage: 'en' };
const family = (role: 'EDITOR' | 'VIEWER') => ({ id: 'f1', name: 'Reddy family', myRole: role, myMemberId: 'm1' });
const people = [
  { id: 'p1', name: 'Thatha', voiceLanguage: 'te', timeZone: 'Asia/Kolkata', linkedPhones: 1, medicineCount: 3, hasPhoto: false },
  { id: 'p2', name: 'Paati', voiceLanguage: 'ta', timeZone: 'Asia/Kolkata', linkedPhones: 0, medicineCount: 0, hasPhoto: false },
];
const members = [
  { id: 'm1', userId: 'u1', displayName: 'Sowjanya', email: 'a@b.com', role: 'EDITOR', me: true },
  { id: 'm2', userId: 'u2', displayName: 'Dad', email: 'd@b.com', role: 'VIEWER', me: false },
];

beforeEach(async () => {
  await i18n.changeLanguage('en');
  tokenStore.set('user-token');
});
afterEach(() => {
  vi.restoreAllMocks();
  tokenStore.set(null);
  localStorage.clear();
});

describe('Family home', () => {
  it('offers to create or join a family when the user has none', async () => {
    const calls = mockApi({
      'GET /api/auth/me': () => ({ body: me }),
      'GET /api/families/me': () => ({ status: 404, body: { status: 404 } }),
      'POST /api/pairing/join': () => ({ status: 400, body: { status: 400, detail: 'bad' } }),
    });
    renderAt('/', [{ path: '/', element: <FamilyHomePage /> }]);

    expect(await screen.findByRole('heading', { name: 'Set up your family' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Create a family' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Family code'), { target: { value: 'k7qm-4xpa' } });
    fireEvent.click(screen.getByRole('button', { name: 'Join' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This code is wrong, already used, or expired');
    expect(calls.find((c) => c.key === 'POST /api/pairing/join')?.body).toEqual({ code: 'K7QM-4XPA' });
  });
});

describe('Family members screen', () => {
  const routes = (role: 'EDITOR' | 'VIEWER') => ({
    'GET /api/auth/me': () => ({ body: me }),
    'GET /api/families/me': () => ({ body: family(role) }),
    'GET /api/people': () => ({ body: people }),
    'GET /api/families/me/members': () => ({ body: members }),
    'POST /api/pairing/codes': () => ({
      status: 201,
      body: { code: 'K7QM-4XPA', purpose: 'MEMBER', role: 'VIEWER', expiresAt: '2030-01-01T10:00:00Z' },
    }),
  });

  it('lists people with their voice language and phone status, and family members with roles', async () => {
    mockApi(routes('EDITOR'));
    renderAt('/family', [{ path: '/family', element: <MembersPage /> }]);

    const thatha = (await screen.findByText('Thatha')).closest('li')!;
    expect(within(thatha).getByText('తెలుగు')).toHaveAttribute('lang', 'te');
    expect(within(thatha).getByText('Phone linked')).toBeInTheDocument();
    expect(within(thatha).getByText('3 medicines')).toBeInTheDocument();
    const paati = screen.getByText('Paati').closest('li')!;
    expect(within(paati).getByText('No phone yet')).toBeInTheDocument();

    expect(screen.getByText('You')).toBeInTheDocument();
    expect(screen.getByLabelText('What Dad can do')).toHaveValue('VIEWER');
    expect(screen.getByRole('link', { name: '+ Add a person to remind' })).toHaveAttribute('href', '/family/people/new');
  });

  it('makes an invite code for a new family member', async () => {
    const calls = mockApi(routes('EDITOR'));
    renderAt('/family', [{ path: '/family', element: <MembersPage /> }]);
    fireEvent.click(await screen.findByLabelText('Can edit everything'));
    fireEvent.click(screen.getByRole('button', { name: 'Make an invite code' }));
    expect(await screen.findByText('K7QM-4XPA')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'POST /api/pairing/codes')?.body).toEqual({ purpose: 'MEMBER', role: 'EDITOR' });
  });

  it('hides editing controls from viewers', async () => {
    mockApi(routes('VIEWER'));
    renderAt('/family', [{ path: '/family', element: <MembersPage /> }]);
    await screen.findByText('Thatha');
    expect(screen.queryByRole('link', { name: '+ Add a person to remind' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Make an invite code' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('What Dad can do')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Leave family' })).toBeInTheDocument();
  });
});

describe('Set up phone screen', () => {
  it('counts steps, ticks step 1 once a phone is linked, and remembers ticks', async () => {
    mockApi({
      'GET /api/auth/me': () => ({ body: me }),
      'GET /api/families/me': () => ({ body: family('EDITOR') }),
      'GET /api/people/p1': () => ({ body: people[0] }),
      'GET /api/devices?personId=p1': () => ({
        body: [{ id: 'd1', personId: 'p1', name: 'Redmi 12', platform: 'android', createdAt: '2026-11-16T05:00:00Z' }],
      }),
    });
    renderAt('/family/people/p1/setup', [{ path: '/family/people/:id/setup', element: <SetupPhonePage /> }]);

    expect(await screen.findByRole('heading', { name: 'Set up Thatha’s phone' })).toBeInTheDocument();
    expect(screen.getByText('1 of 5 steps done')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1');
    expect(await screen.findByText('Redmi 12')).toBeInTheDocument();

    const notifications = screen.getByRole('button', { name: /Allow notifications/ });
    expect(notifications).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(notifications);
    expect(notifications).toHaveAttribute('aria-pressed', 'true');
    await waitFor(() => expect(screen.getByText('2 of 5 steps done')).toBeInTheDocument());
    expect(JSON.parse(localStorage.getItem('mr.setup.p1')!)).toEqual({ 2: true });
  });
});
