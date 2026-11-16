import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import '../i18n';
import { deviceStore } from '../device/deviceStore';
import { mockApi, renderAt } from '../test-utils';
import { ElderPairPage } from './ElderPairPage';
import { ElderTodayPage } from './ElderTodayPage';

const routes = [
  { path: '/elder', element: <ElderTodayPage /> },
  { path: '/elder/pair', element: <ElderPairPage /> },
];
const thatha = { id: 'p1', name: 'Thatha', voiceLanguage: 'te', timeZone: 'Asia/Kolkata', linkedPhones: 1 };
const syncFor = (person: { id: string; name: string; voiceLanguage: string; timeZone: string }) => ({
  person, familyName: 'Reddy family', medicines: [], logs: [], serverTime: new Date().toISOString(), pushEnabled: false,
});

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('Elder phone pairing', () => {
  it('asks for the code in Telugu, then "which person", then shows the Today screen', async () => {
    const calls = mockApi({
      'POST /api/pairing/lookup': () => ({
        body: {
          familyName: 'Reddy family',
          purpose: 'DEVICE',
          people: [
            { id: 'p1', name: 'Thatha', voiceLanguage: 'te' },
            { id: 'p2', name: 'Paati', voiceLanguage: 'ta' },
          ],
        },
      }),
      'POST /api/pairing/redeem': () => ({
        status: 201,
        body: { deviceToken: 'dev-token', deviceId: 'd1', familyName: 'Reddy family', person: thatha },
      }),
      'GET /api/devices/me/sync': () => ({ body: syncFor(thatha) }),
    });

    // Not paired yet: /elder sends us to pairing, shown in Telugu first.
    renderAt('/elder', routes);
    expect(await screen.findByRole('heading', { name: 'ఈ ఫోన్‌ను లింక్ చేయండి' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('కుటుంబ కోడ్'), { target: { value: 'k7qm4xpa' } });
    fireEvent.click(screen.getByRole('button', { name: 'తర్వాత' }));

    expect(await screen.findByRole('heading', { name: 'ఈ ఫోన్ ఎవరిది?' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Thatha/ }));

    expect(await screen.findByRole('heading', { name: 'ఈ రోజు' })).toBeInTheDocument();
    expect(deviceStore.get()?.token).toBe('dev-token');
    expect(calls.find((c) => c.key === 'POST /api/pairing/redeem')?.body).toMatchObject({ code: 'K7QM4XPA', personId: 'p1' });
    // Pairing calls send no login; the Today screen uses the phone's own token.
    expect(calls.find((c) => c.key === 'POST /api/pairing/lookup')?.auth).toBeNull();
    expect(calls.find((c) => c.key === 'GET /api/devices/me/sync')?.auth).toBe('Bearer dev-token');
  });

  it('shows a clear error for a bad code', async () => {
    mockApi({ 'POST /api/pairing/lookup': () => ({ status: 400, body: { status: 400 } }) });
    renderAt('/elder/pair', routes);
    fireEvent.change(await screen.findByLabelText('కుటుంబ కోడ్'), { target: { value: 'AAAABBBB' } });
    fireEvent.click(screen.getByRole('button', { name: 'తర్వాత' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('ఈ కోడ్ తప్పు');
  });

  it('shows the Today screen in the person’s own voice language', async () => {
    const paati = { ...thatha, voiceLanguage: 'ta', name: 'Paati' };
    deviceStore.set({ token: 't', deviceId: 'd1', familyName: 'Reddy family', person: paati });
    mockApi({ 'GET /api/devices/me/sync': () => ({ body: syncFor(paati) }) });
    renderAt('/elder', routes);
    expect(await screen.findByRole('heading', { name: 'இன்று' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveAttribute('lang', 'ta');
  });

  it('goes back to pairing when the family unlinks the phone', async () => {
    deviceStore.set({ token: 'old', deviceId: 'd1', familyName: 'Reddy family', person: thatha });
    mockApi({ 'GET /api/devices/me/sync': () => ({ status: 401, body: {} }) });
    renderAt('/elder', routes);
    expect(await screen.findByRole('heading', { name: 'ఈ ఫోన్‌ను లింక్ చేయండి' })).toBeInTheDocument();
    expect(deviceStore.get()).toBeNull();
  });
});
