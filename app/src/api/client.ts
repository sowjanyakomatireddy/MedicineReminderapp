const BASE = import.meta.env.VITE_API_URL ?? '';
const TOKEN_KEY = 'mr.token';

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fieldErrors?: Record<string, string>,
  ) {
    super(message);
  }
}

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* ignore */
    }
  },
};

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, 'network');
  }
  if (res.status === 401) tokenStore.set(null);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body.detail ?? res.statusText, body.errors);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

/** Maps an API error to an i18n key for display. */
export function errorKey(err: unknown): string {
  if (!(err instanceof ApiError)) return 'errors.generic';
  switch (err.status) {
    case 0:
      return 'errors.network';
    case 401:
      return 'errors.wrongCredentials';
    case 409:
      return 'errors.emailTaken';
    case 429:
      return 'errors.tooMany';
    default:
      return 'errors.generic';
  }
}
