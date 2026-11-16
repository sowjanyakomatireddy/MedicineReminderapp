const BASE = import.meta.env.VITE_API_URL ?? '';

/**
 * The backend's full address, for the Android app's native sync (it cannot use a relative URL).
 * Empty in a browser dev build, where Vite forwards /api.
 */
export const API_BASE: string = BASE;
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

export interface ApiInit extends RequestInit {
  /**
   * Which bearer token to send. Leave out to send the family member's login token.
   * The elder's paired phone passes its device token here.
   */
  token?: string | null;
}

export async function api<T>(path: string, init: ApiInit = {}): Promise<T> {
  const { token: explicitToken, ...rest } = init;
  const headers = new Headers(rest.headers);
  // FormData (photo uploads) sets its own multipart Content-Type.
  if (rest.body && !(rest.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const usesLogin = explicitToken === undefined;
  const token = usesLogin ? tokenStore.get() : explicitToken;
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { ...rest, headers });
  } catch {
    throw new ApiError(0, 'network');
  }
  if (res.status === 401 && usesLogin) tokenStore.set(null);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body.detail ?? res.statusText, body.errors);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

/** Fetches an image that needs the login token (an <img src> cannot send it) and returns an object URL. */
export async function fetchImage(path: string): Promise<string> {
  const token = tokenStore.get();
  const res = await fetch(`${BASE}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) throw new ApiError(res.status, res.statusText);
  return URL.createObjectURL(await res.blob());
}

/**
 * Maps an API error to an i18n key for display. `overrides` gives screen-specific keys by HTTP status,
 * e.g. { 409: 'errors.emailTaken' } on the register screen.
 */
export function errorKey(err: unknown, overrides: Partial<Record<number, string>> = {}): string {
  if (!(err instanceof ApiError)) return 'errors.generic';
  const override = overrides[err.status];
  if (override) return override;
  switch (err.status) {
    case 0:
      return 'errors.network';
    case 401:
      return 'errors.wrongCredentials';
    case 403:
      return 'errors.forbidden';
    case 409:
      return 'errors.conflict';
    case 429:
      return 'errors.tooMany';
    default:
      return 'errors.generic';
  }
}
