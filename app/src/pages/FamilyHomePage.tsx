import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { LanguageSwitcher } from '../components/LanguageSwitcher';

/** Placeholder: the real Family Home (today's status per person) comes in Phase 3. */
export function FamilyHomePage() {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col gap-6 px-4 py-6">
      <header className="flex items-center justify-between">
        <span className="text-xl font-bold text-teal-dark">{t('appName')}</span>
        <LanguageSwitcher />
      </header>
      <h1 className="text-3xl font-bold">{t('home.welcome', { name: user?.displayName })}</h1>
      <p className="rounded-2xl bg-white p-4 text-muted">{t('home.comingSoon')}</p>
      <p className="text-sm text-muted">{t('common.disclaimer')}</p>
      <button onClick={logout} className="min-h-14 rounded-2xl border-2 border-teal font-semibold text-teal">
        {t('common.logout')}
      </button>
    </main>
  );
}
