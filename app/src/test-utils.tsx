import type { ReactNode } from 'react';
import { vi } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './auth/AuthContext';

type Handler = (init?: RequestInit) => { status?: number; body?: unknown };

/** Fakes the API: keys are "METHOD /path", values return a status and JSON body. Unknown calls get 404. */
export function mockApi(routes: Record<string, Handler>) {
  const calls: { key: string; body?: unknown; auth?: string | null }[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input), 'http://localhost');
    const key = `${init?.method ?? 'GET'} ${url.pathname}${url.search}`;
    const auth = new Headers(init?.headers).get('Authorization');
    calls.push({ key, body: init?.body ? JSON.parse(String(init.body)) : undefined, auth });
    const handler = routes[key];
    const { status = 200, body = {} } = handler ? handler(init) : { status: 404, body: { status: 404 } };
    return new Response(status === 204 ? null : JSON.stringify(body), { status });
  });
  return calls;
}

/** Renders a page at a URL with the same providers as the real app. */
export function renderAt(path: string, routes: { path: string; element: ReactNode }[]) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <Routes>
            {routes.map((r) => (
              <Route key={r.path} path={r.path} element={r.element} />
            ))}
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
