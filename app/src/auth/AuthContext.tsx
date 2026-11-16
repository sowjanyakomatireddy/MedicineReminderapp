import { createContext, useCallback, useContext, useMemo, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, tokenStore } from '../api/client';
import i18n from '../i18n';

export interface User {
  id: string;
  email: string;
  displayName: string;
  appLanguage: string;
}
interface AuthResponse {
  token: string;
  expiresInSeconds: number;
  user: User;
}
interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: { email: string; password: string; displayName: string; appLanguage: string }) => Promise<void>;
  logout: () => void;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const me = useQuery({
    queryKey: ['me'],
    queryFn: () => api<User>('/api/auth/me'),
    enabled: !!tokenStore.get(),
    retry: false,
  });

  const accept = useCallback(
    (r: AuthResponse) => {
      tokenStore.set(r.token);
      qc.setQueryData(['me'], r.user);
      void i18n.changeLanguage(r.user.appLanguage);
    },
    [qc],
  );

  const value = useMemo<AuthState>(
    () => ({
      user: tokenStore.get() ? (me.data ?? null) : null,
      loading: !!tokenStore.get() && me.isLoading,
      login: async (email, password) =>
        accept(await api<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })),
      register: async (input) =>
        accept(await api<AuthResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify(input) })),
      logout: () => {
        tokenStore.set(null);
        qc.clear();
      },
    }),
    [me.data, me.isLoading, accept, qc],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth must be used inside AuthProvider');
  return v;
}
