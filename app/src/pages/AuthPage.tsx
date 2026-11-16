import { useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { ApiError, errorKey } from '../api/client';
import { Alert, Button, TextField } from '../components/ui';
import { LanguageSwitcher } from '../components/LanguageSwitcher';

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { t, i18n } = useTranslation();
  const { user, login, register } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const isLogin = mode === 'login';
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : undefined;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (isLogin) await login(email, password);
      else await register({ email, password, displayName, appLanguage: i18n.resolvedLanguage ?? 'en' });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col gap-6 px-4 py-8">
      <div className="flex items-center justify-between">
        <span className="text-xl font-bold text-teal-dark">{t('appName')}</span>
        <LanguageSwitcher />
      </div>
      <h1 className="text-3xl font-bold">{isLogin ? t('auth.loginTitle') : t('auth.registerTitle')}</h1>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {!isLogin && (
          <TextField
            label={t('auth.displayName')}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            autoComplete="name"
            required
            error={fieldErrors?.displayName}
          />
        )}
        <TextField
          label={t('auth.email')}
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          required
          error={fieldErrors?.email}
        />
        <TextField
          label={t('auth.password')}
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete={isLogin ? 'current-password' : 'new-password'}
          hint={isLogin ? undefined : t('auth.passwordHint')}
          required
          minLength={8}
          error={fieldErrors?.password}
        />
        {error != null && !fieldErrors && <Alert>{t(errorKey(error))}</Alert>}
        <Button type="submit" disabled={busy}>
          {isLogin ? t('auth.login') : t('auth.register')}
        </Button>
      </form>
      <p className="text-center">
        {isLogin ? t('auth.noAccount') : t('auth.haveAccount')}{' '}
        <Link to={isLogin ? '/register' : '/login'} className="font-semibold text-teal underline">
          {isLogin ? t('auth.register') : t('auth.login')}
        </Link>
      </p>
    </main>
  );
}
