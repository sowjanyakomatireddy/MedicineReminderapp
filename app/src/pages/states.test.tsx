import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import i18n from '../i18n';
import { tokenStore } from '../api/client';
import { mockApi, renderAt } from '../test-utils';
import { CrashScreen, FamilyPage } from '../components/layout';
import { PersonFormPage } from './PersonFormPage';
import { RoutinePage } from './RoutinePage';

const me = { id: 'u1', email: 'a@b.com', displayName: 'Sowjanya', appLanguage: 'en' };
const family = { id: 'f1', name: 'Reddy family', myRole: 'EDITOR', myMemberId: 'm1' };

beforeEach(async () => {
  await i18n.changeLanguage('en');
  tokenStore.set('user-token');
});
afterEach(() => {
  vi.restoreAllMocks();
  tokenStore.set(null);
});

describe('error, empty and offline states', () => {
  it('editing a person that fails to load shows an error with retry, never an empty "Add" form', async () => {
    let fail = true;
    mockApi({
      'GET /api/auth/me': () => ({ body: me }),
      'GET /api/families/me': () => ({ body: family }),
      'GET /api/people/p1': () => (fail ? { status: 500, body: {} } : {
        body: { id: 'p1', name: 'Thatha', voiceLanguage: 'te', timeZone: 'Asia/Kolkata', linkedPhones: 0, medicineCount: 0, hasPhoto: false, periodTracking: false },
      }),
    });
    renderAt('/family/people/p1', [{ path: '/family/people/:id', element: <PersonFormPage /> }]);
    expect(await screen.findByText('Could not load. Check your internet and try again.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Add a person to remind' })).not.toBeInTheDocument();

    fail = false;
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('heading', { name: 'Edit Thatha' })).toBeInTheDocument();
  });

  it('shows an empty routine message, and a retry if the routine fails', async () => {
    mockApi({
      'GET /api/auth/me': () => ({ body: me }),
      'GET /api/families/me': () => ({ body: family }),
      'GET /api/people': () => ({ body: [{ id: 'p1', name: 'Thatha', voiceLanguage: 'te', timeZone: 'Asia/Kolkata', linkedPhones: 0, medicineCount: 0, hasPhoto: false, periodTracking: false }] }),
      'GET /api/people/p1/routine': () => ({ status: 500, body: {} }),
    });
    renderAt('/routine', [{ path: '/routine', element: <RoutinePage /> }]);
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('says so when the family app goes offline', async () => {
    render(<FamilyPage nav={false}><p>page</p></FamilyPage>);
    expect(screen.queryByText(/You are offline/)).not.toBeInTheDocument();
    act(() => { window.dispatchEvent(new Event('offline')); });
    expect(screen.getByRole('status')).toHaveTextContent('You are offline');
    act(() => { window.dispatchEvent(new Event('online')); });
    expect(screen.queryByText(/You are offline/)).not.toBeInTheDocument();
  });

  it('a crashing screen shows a calm message and Reload, not a blank page', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const Broken = () => { throw new Error('boom'); };
    render(<CrashScreen message="Something went wrong on this screen." action="Reload"><Broken /></CrashScreen>);
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong on this screen.');
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument();
  });
});
