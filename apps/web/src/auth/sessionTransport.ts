/**
 * WEB-01-AUTH-TRANSPORT (unit-tested but NOT wired to the UI).
 *
 * Strict same-origin, HTTPS-only browser session transport for a future
 * independently approved rollout. This is not a replacement for Springdoc /
 * Orval DTOs or an authorization mechanism. Browser/Java/Caddy gates remain
 * closed; never import it into the synthetic preview to perform live login.
 *
 * Only the backend's established protocol is expressed here:
 * GET csrf -> POST form login -> GET fresh csrf (session fixation) ->
 * GET session, and fresh csrf -> POST logout. Cookies remain HttpOnly,
 * credentials are never written to localStorage, URLs or logs.
 */
export type ConsoleFetch = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export type LoginResult =
  | { kind: 'authenticated'; login: string }
  | { kind: 'signed_out' }
  | { kind: 'invalid_credentials' }
  | { kind: 'forbidden' }
  | { kind: 'throttled' }
  | { kind: 'unavailable' };

export type LogoutResult = 'signed_out' | 'forbidden' | 'unavailable';

export function createSessionTransport(httpsOrigin: string, fetcher: ConsoleFetch) {
  const base = new URL(httpsOrigin);
  if (base.protocol !== 'https:' || base.origin !== httpsOrigin ||
      base.pathname !== '/' || base.search || base.hash ||
      base.username || base.password) {
    throw new Error('Canonical HTTPS console origin required');
  }

  const request = (path: string, init: RequestInit = {}) =>
    fetcher(new URL(path, base), {
      credentials: 'same-origin',
      mode: 'same-origin',
      cache: 'no-store',
      redirect: 'error',
      referrerPolicy: 'no-referrer',
      ...init,
    });

  // Refuse an unexpected/missing token, a non-JSON body, or a cacheable
  // response. Never retain a CSRF token beyond the particular operation.
  async function freshCsrf(): Promise<string | null> {
    try {
      const response = await request('/api/v1/auth/csrf');
      if (response.status !== 200 ||
          !response.headers.get('content-type')?.toLowerCase().includes('application/json') ||
          !response.headers.get('cache-control')?.toLowerCase().includes('no-store')) {
        return null;
      }
      const data: unknown = await response.json();
      if (!data || typeof data !== 'object' || !('csrfToken' in data) ||
          typeof data.csrfToken !== 'string' || !data.csrfToken.trim()) {
        return null;
      }
      return data.csrfToken;
    } catch {
      return null;
    }
  }

  async function session(): Promise<LoginResult> {
    try {
      const response = await request('/api/v1/auth/session');
      if (response.status === 401) return { kind: 'signed_out' };
      if (response.status === 403) return { kind: 'forbidden' };
      if (response.status !== 200 ||
          !response.headers.get('content-type')?.toLowerCase().includes('application/json') ||
          !response.headers.get('cache-control')?.toLowerCase().includes('no-store')) {
        return { kind: 'unavailable' };
      }
      const data: unknown = await response.json();
      if (!data || typeof data !== 'object' || !('login' in data) ||
          typeof data.login !== 'string' || !data.login.trim()) {
        return { kind: 'unavailable' };
      }
      return { kind: 'authenticated', login: data.login };
    } catch {
      return { kind: 'unavailable' };
    }
  }

  async function login(username: string, password: string): Promise<LoginResult> {
    const token = await freshCsrf();
    if (!token) return { kind: 'unavailable' };
    try {
      const body = new URLSearchParams({ username, password });
      const response = await request('/api/v1/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-CSRF-TOKEN': token,
        },
        body,
      });
      if (response.status === 401) return { kind: 'invalid_credentials' };
      if (response.status === 403) return { kind: 'forbidden' };
      if (response.status === 429) return { kind: 'throttled' };
      if (response.status !== 204) return { kind: 'unavailable' };

      // Session fixation protection changes the session id on success.
      // A fresh token is required before any subsequent authenticated action.
      if (!(await freshCsrf())) return { kind: 'unavailable' };
      // Successful form login alone does not prove the current account is
      // enabled or has any scoped REST privileges.
      return await session();
    } catch {
      return { kind: 'unavailable' };
    }
  }

  async function logout(): Promise<LogoutResult> {
    const token = await freshCsrf();
    if (!token) return 'unavailable';
    try {
      const response = await request('/api/v1/auth/logout', {
        method: 'POST',
        headers: { 'X-CSRF-TOKEN': token },
      });
      if (response.status === 204) return 'signed_out';
      if (response.status === 403) return 'forbidden';
      return 'unavailable';
    } catch {
      return 'unavailable';
    }
  }

  return { login, logout, session };
}
