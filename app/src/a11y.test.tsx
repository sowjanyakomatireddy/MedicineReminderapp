import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import axe from 'axe-core';
import i18n from './i18n';
import { tokenStore } from './api/client';
import { deviceStore } from './device/deviceStore';
import { mockApi, renderAt } from './test-utils';
import { AuthPage } from './pages/AuthPage';
import { FamilyHomePage } from './pages/FamilyHomePage';
import { MembersPage } from './pages/MembersPage';
import { MedicineFormPage } from './pages/MedicineFormPage';
import { SetupPhonePage } from './pages/SetupPhonePage';
import { MissedAlertPage } from './pages/MissedAlertPage';
import { RoutinePage } from './pages/RoutinePage';
import { PersonFormPage } from './pages/PersonFormPage';
import { ElderPairPage } from './pages/ElderPairPage';
import { ElderTodayPage } from './pages/ElderTodayPage';

/**
 * Automated accessibility checks (axe-core) on every main screen: labels, names, roles, ARIA, landmarks,
 * duplicate ids. Colour contrast is checked separately (jsdom has no layout to measure it).
 */
async function expectAccessible() {
  const results = await axe.run(document.body, {
    rules: { 'color-contrast': { enabled: false }, region: { enabled: false } },
  });
  const problems = results.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).join(', ')})`);
  expect(problems).toEqual([]);
}

const me = { id: 'u1', email: 'a@b.com', displayName: 'Sowjanya', appLanguage: 'en' };
const family = { id: 'f1', name: 'Reddy family', myRole: 'EDITOR', myMemberId: 'm1' };
const amma = { id: 'p2', name: 'అమ్మ', voiceLanguage: 'te', timeZone: 'Asia/Kolkata', linkedPhones: 1, medicineCount: 1, hasPhoto: false, periodTracking: true, phone: '+91 90000 00000' };
const thatha = { ...amma, id: 'p1', name: 'తాతయ్య', periodTracking: false };
const missed = {
  id: 'd1', personId: 'p1', personName: 'తాతయ్య', personPhone: '+91 90000 00000', medicineId: 'm2', medicineName: 'Sugar tablet',
  hasPhoto: false, food: 'AFTER', date: '2026-11-16', time: '13:30', status: 'MISSED', source: 'SERVER',
  loggedAt: '2026-11-16T08:32:00Z', rings: 7, phoneLinked: true, pushEnabled: true,
};
const week = Array.from({ length: 7 }, (_, i) => ({ date: `2026-11-${10 + i}`, total: 2, taken: 2, missed: 0 }));
const sync = {
  person: { id: 'p1', name: 'తాతయ్య', voiceLanguage: 'te', timeZone: 'Asia/Kolkata', familyPhone: '+91 98765 43210' },
  familyName: 'Reddy family', logs: [], serverTime: '', pushEnabled: false,
  medicines: [{ id: 'm1', personId: 'p1', name: 'Amlodipine', voiceName: 'బీపీ మాత్ర', hasPhoto: false, updatedAt: '',
    schedules: [{ id: 's1', time: '23:59', days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'], food: 'BEFORE', remindBeforeMin: 5, repeatEveryMin: 5, stopAfterMin: 30, active: true }] }],
};

beforeEach(async () => {
  await i18n.changeLanguage('en');
  tokenStore.set('user-token');
  mockApi({
    'GET /api/auth/me': () => ({ body: me }),
    'GET /api/families/me': () => ({ body: family }),
    'GET /api/people': () => ({ body: [thatha, amma] }),
    'GET /api/people/p1': () => ({ body: thatha }),
    'GET /api/families/me/members': () => ({ body: [{ id: 'm1', userId: 'u1', displayName: 'Sowjanya', email: 'a@b.com', role: 'EDITOR', me: true }, { id: 'm2', userId: 'u2', displayName: 'Dad', email: 'd@b.com', role: 'VIEWER', me: false }] }),
    'GET /api/families/me/today': () => ({ body: [{ personId: 'p1', name: 'తాతయ్య', voiceLanguage: 'te', date: '2026-11-16', total: 1, taken: 0, doses: [{ scheduleId: 's2', medicineId: 'm2', name: 'Sugar tablet', hasPhoto: false, time: '13:30', alertAt: '13:25', food: 'AFTER', status: 'MISSED', doseLogId: 'd1' }] }] }),
    'GET /api/dose-logs/alerts': () => ({ body: [missed] }),
    'GET /api/dose-logs/d1': () => ({ body: missed }),
    'GET /api/people/p1/week': () => ({ body: week }),
    'GET /api/devices?personId=p1': () => ({ body: [{ id: 'd1', personId: 'p1', name: 'Redmi 12', platform: 'android', createdAt: '2026-11-16T05:00:00Z' }] }),
    'GET /api/people/p2/routine': () => ({ body: [{ id: 'r1', personId: 'p2', time: '06:30', days: ['MONDAY'], label: 'Drink water', spokenText: 'నీళ్ళు తాగండి', active: true }] }),
    'GET /api/people/p2/period': () => ({ body: { enabled: true, cycleLengthDays: 28, averageCycleDays: 29, periodLengthDays: 5, remindDaysBefore: 2, remindTime: '09:00', today: '2026-11-16', lastStart: '2026-10-22', nextExpected: '2026-11-20', daysUntil: 4, reminderDate: '2026-11-18', recentStarts: ['2026-10-22'] } }),
    'GET /api/devices/me/sync': () => ({ body: sync }),
  });
});
afterEach(() => {
  vi.restoreAllMocks();
  tokenStore.set(null);
  localStorage.clear();
});

describe('accessibility (axe)', () => {
  it('login', async () => {
    tokenStore.set(null);
    renderAt('/login', [{ path: '/login', element: <AuthPage mode="login" /> }]);
    await screen.findByRole('heading', { name: 'Log in' });
    await expectAccessible();
  });

  it('Family Home with a missed dose', async () => {
    renderAt('/', [{ path: '/', element: <FamilyHomePage /> }]);
    await screen.findByText('0 of 1');
    await expectAccessible();
  });

  it('Family members', async () => {
    renderAt('/family', [{ path: '/family', element: <MembersPage /> }]);
    await screen.findByText('Dad');
    await expectAccessible();
  });

  it('Add a medicine', async () => {
    renderAt('/medicines/new?person=p1', [{ path: '/medicines/new', element: <MedicineFormPage /> }]);
    await screen.findByLabelText('Medicine name');
    await expectAccessible();
  });

  it('Person form', async () => {
    renderAt('/family/people/p1', [{ path: '/family/people/:id', element: <PersonFormPage /> }]);
    await screen.findByLabelText('Name');
    await expectAccessible();
  });

  it('Set up phone', async () => {
    renderAt('/family/people/p1/setup', [{ path: '/family/people/:id/setup', element: <SetupPhonePage /> }]);
    await screen.findByText('Redmi 12');
    await expectAccessible();
  });

  it('Missed alert', async () => {
    renderAt('/missed/d1', [{ path: '/missed/:id', element: <MissedAlertPage /> }]);
    await screen.findByText(/Today: 2 of 2 taken/);
    await expectAccessible();
  });

  it('Daily routine with period card', async () => {
    renderAt('/routine?person=p2', [{ path: '/routine', element: <RoutinePage /> }]);
    await screen.findByText('Drink water');
    await screen.findByText('In 4 days');
    await expectAccessible();
  });

  it('elder pairing', async () => {
    renderAt('/elder/pair', [{ path: '/elder/pair', element: <ElderPairPage /> }]);
    await screen.findByLabelText('కుటుంబ కోడ్');
    await expectAccessible();
  });

  it('elder Today', async () => {
    deviceStore.set({ token: 't', deviceId: 'd1', familyName: 'Reddy family', person: sync.person });
    renderAt('/elder', [{ path: '/elder', element: <ElderTodayPage /> }]);
    await screen.findByText('బీపీ మాత్ర');
    await expectAccessible();
  });
});
