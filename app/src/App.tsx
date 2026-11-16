import { Navigate, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './auth/AuthContext';
import { useFamily } from './api/family';
import { Loading } from './components/layout';
import { AuthPage } from './pages/AuthPage';
import { FamilyHomePage } from './pages/FamilyHomePage';
import { MembersPage } from './pages/MembersPage';
import { PersonFormPage } from './pages/PersonFormPage';
import { SetupPhonePage } from './pages/SetupPhonePage';
import { MedicinesPage } from './pages/MedicinesPage';
import { MissedAlertPage } from './pages/MissedAlertPage';
import { RoutinePage } from './pages/RoutinePage';
import { MedicineFormPage } from './pages/MedicineFormPage';
import { ElderPairPage } from './pages/ElderPairPage';
import { ElderTodayPage } from './pages/ElderTodayPage';
import { ElderSetupPage } from './pages/ElderSetupPage';
import { ElderDebugPage } from './pages/ElderDebugPage';
import { isNativeApp } from './native/medAlarm';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <Loading />;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

/** Family screens other than Home need a family; Home shows "create or join" when there is none. */
function RequireFamily({ children }: { children: ReactNode }) {
  const family = useFamily();
  if (family.isPending) return <Loading />;
  if (family.data === null) return <Navigate to="/" replace />;
  return children;
}

const family = (page: ReactNode) => (
  <RequireAuth>
    <RequireFamily>{page}</RequireFamily>
  </RequireAuth>
);

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      {/* The grandfather's phone does not log in with email; it is paired with a family code. */}
      <Route path="/elder" element={<ElderTodayPage />} />
      <Route path="/elder/pair" element={<ElderPairPage />} />
      <Route path="/elder/setup" element={<ElderSetupPage />} />
      <Route path="/elder/debug" element={<ElderDebugPage />} />
      {/* The Android app is the elder's phone, so it opens on the elder screens. */}
      <Route path="/" element={isNativeApp() ? <Navigate to="/elder" replace /> : <RequireAuth><FamilyHomePage /></RequireAuth>} />
      <Route path="/medicines" element={family(<MedicinesPage />)} />
      <Route path="/medicines/new" element={family(<MedicineFormPage />)} />
      <Route path="/medicines/:id" element={family(<MedicineFormPage />)} />
      <Route path="/missed/:id" element={family(<MissedAlertPage />)} />
      <Route path="/routine" element={family(<RoutinePage />)} />
      <Route path="/family" element={family(<MembersPage />)} />
      <Route path="/family/people/new" element={family(<PersonFormPage />)} />
      <Route path="/family/people/:id" element={family(<PersonFormPage />)} />
      <Route path="/family/people/:id/setup" element={family(<SetupPhonePage />)} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
