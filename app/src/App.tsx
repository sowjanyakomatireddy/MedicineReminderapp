import { Navigate, Route, Routes } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { ReactNode } from 'react';
import { useAuth } from './auth/AuthContext';
import { AuthPage } from './pages/AuthPage';
import { FamilyHomePage } from './pages/FamilyHomePage';
import { ElderTodayPage } from './pages/ElderTodayPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const { t } = useTranslation();
  if (loading) return <p className="p-6 text-lg" role="status">{t('common.loading')}</p>;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      {/* The grandfather's phone does not log in with email; it is paired by family code (Phase 2). */}
      <Route path="/elder" element={<ElderTodayPage />} />
      <Route path="/" element={<RequireAuth><FamilyHomePage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
