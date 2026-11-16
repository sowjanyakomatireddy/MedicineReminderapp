import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import '../i18n';
import { deviceStore } from '../device/deviceStore';
import { renderAt } from '../test-utils';
import { alarmProfile } from './alarmConfig';

// A fake of the Java plugin, so the setup screen can be tested in the browser test runner.
const plugin = vi.hoisted(() => ({
  native: true,
  getStatus: vi.fn(),
  checkVoice: vi.fn(),
  setProfile: vi.fn(),
  openSettings: vi.fn(),
  installVoice: vi.fn(),
  testAlarm: vi.fn(),
  requestNotifications: vi.fn(),
}));
vi.mock('./medAlarm', () => ({ isNativeApp: () => plugin.native, MedAlarm: plugin }));

const { ElderSetupPage } = await import('../pages/ElderSetupPage');

const thatha = { id: 'p1', name: 'తాతయ్య', voiceLanguage: 'te', timeZone: 'Asia/Kolkata' };

describe('what the phone gets to build alarms by itself', () => {
  it('sends the raw Telugu sentence template and word lists', () => {
    const p = alarmProfile(thatha);
    expect(p.lang).toBe('te-IN');
    expect(p.timeZone).toBe('Asia/Kolkata');
    // Blanks are left in for AlarmBuilder.java to fill after a background sync.
    expect(p.templates.sentence).toBe('{{person}}, ఇది {{voiceName}}. {{foodPhrase}}.');
    expect(p.templates.foodPhrase.BEFORE).toBe('భోజనానికి ముందు వేసుకోండి');
    expect(p.templates.food.AFTER).toBe('భోజనం తర్వాత');
    expect(p.templates.shape.ROUND).toBe('గుండ్రని');
    expect(p.templates.colour.WHITE).toBe('తెల్లని');
    expect(p.ui.taken).toBe('వేసుకున్నాను');
    expect(p.ui.takenLine).toBe('{{name}} వేసుకున్నారు');
    expect(p.ui.takenTitle).toBe('చాలా బాగుంది!');
  });
});

describe('Elder phone setup screen', () => {
  beforeEach(() => {
    deviceStore.set({ token: 't', deviceId: 'd1', familyName: 'Reddy family', person: thatha });
    plugin.native = true;
    plugin.getStatus.mockResolvedValue({
      manufacturer: 'Xiaomi', model: 'Redmi 12', sdk: 34,
      notifications: true, exactAlarms: false, fullScreen: true, batteryUnrestricted: false,
      autostartPage: true, xiaomiLockScreenPage: true, notificationPermission: 'granted',
    });
    plugin.checkVoice.mockResolvedValue({ status: 'missingData' });
    plugin.testAlarm.mockResolvedValue({ alarms: [] });
  });
  afterEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('shows each step’s real status and opens the right settings page', async () => {
    renderAt('/elder/setup', [{ path: '/elder/setup', element: <ElderSetupPage /> }]);
    expect(await screen.findByRole('heading', { name: 'ఈ ఫోన్‌ను సెటప్ చేయండి' })).toBeInTheDocument();
    expect(plugin.setProfile).toHaveBeenCalledWith(expect.objectContaining({ lang: 'te-IN', timeZone: 'Asia/Kolkata' }));

    const exact = (await screen.findByText('సరైన సమయానికి అలారాలు')).closest('li')!;
    expect(exact).toHaveTextContent('ఇంకా లేదు');
    fireEvent.click(exact.querySelector('button')!);
    expect(plugin.openSettings).toHaveBeenCalledWith({ target: 'exactAlarm' });

    const notifications = screen.getByText('రిమైండర్‌లు చూపించండి').closest('li')!;
    expect(notifications).toHaveTextContent('పూర్తయింది');

    // Xiaomi extras: autostart and "show on lock screen".
    fireEvent.click(screen.getByRole('button', { name: 'ఆటోస్టార్ట్ అనుమతించండి' }));
    expect(plugin.openSettings).toHaveBeenCalledWith({ target: 'autostart' });
    fireEvent.click(screen.getByRole('button', { name: '“లాక్ స్క్రీన్‌పై చూపించు” అనుమతించండి' }));
    expect(plugin.openSettings).toHaveBeenCalledWith({ target: 'lockScreen' });

    // Telugu voice missing → install button.
    await waitFor(() => expect(screen.getByRole('button', { name: 'వాయిస్ ఇన్‌స్టాల్ చేయండి' })).toBeInTheDocument());
    expect(plugin.checkVoice).toHaveBeenCalledWith({ lang: 'te-IN' });
  });

  it('rings a test alarm in 10 seconds with the person’s own sentence', async () => {
    renderAt('/elder/setup', [{ path: '/elder/setup', element: <ElderSetupPage /> }]);
    fireEvent.click(await screen.findByRole('button', { name: '10 సెకన్లలో టెస్ట్ అలారం మోగించండి' }));
    await screen.findByText(/టెస్ట్ అలారం పెట్టాం/);
    expect(plugin.testAlarm).toHaveBeenCalledWith(expect.objectContaining({
      seconds: 10,
      spoken: 'తాతయ్య, ఇది పరీక్ష మాత్ర. భోజనానికి ముందు వేసుకోండి.',
    }));
  });

  it('explains that the checks need the Android app when opened in a browser', async () => {
    plugin.native = false;
    renderAt('/elder/setup', [{ path: '/elder/setup', element: <ElderSetupPage /> }]);
    expect(await screen.findByText('ఈ తనిఖీలు వారి ఫోన్‌లోని Android యాప్‌లో పనిచేస్తాయి.')).toBeInTheDocument();
    expect(plugin.getStatus).not.toHaveBeenCalled();
  });
});
