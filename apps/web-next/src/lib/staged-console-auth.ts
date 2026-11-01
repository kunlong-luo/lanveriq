import { canonicalConsoleOrigin, type BrowserFetch } from './staged-console-read';

/**
 * NXT-04-A: staged AUTH transport for a future HTTPS-only browser pilot.
 * Source adapted from apps/web/src/auth/sessionTransport.ts, not a new
 * password protocol or an authorization bypass. NEVER import this from
 * Next.js app/pages/components before real Caddy console HTTPS, browser
 * CSRF/Origin/session fixation and PostgreSQL RBAC acceptance.
 *
 * This module does not read browser globals, create a proxy, accept browser
 * cookies manually, persist credentials, or perform background polling.
 */
export type StagedSessionState =
  | Readonly<{ kind: 'authenticated'; login: string }>
  | Readonly<{ kind: 'signed_out' | 'invalid_credentials' | 'forbidden' | 'throttled' | 'unavailable' }>;
export type StagedLogoutState = 'signed_out' | 'forbidden' | 'unavailable';

export function createStagedNextConsoleAuth(
  expectedConsoleOrigin: string,
  browserOrigin: string,
  fetcher: BrowserFetch,
  erasePreviousAuthorizedRows: () => Promise<void>,
) {
  const origin = canonicalConsoleOrigin(expectedConsoleOrigin);
  if (canonicalConsoleOrigin(browserOrigin) !== origin) {
    throw new Error('Console browser origin mismatch');
  }

  function request(path: string, init: RequestInit = {}): Promise<Response> {
    return fetcher(new URL(path, origin), {
      ...init,
      credentials: 'same-origin',
      mode: 'same-origin',
      cache: 'no-store',
      redirect: 'error',
      referrerPolicy: 'no-referrer',
    });
  }

  function strictJson(response: Response): boolean {
    const cache = response.headers.get('cache-control');
    return !response.redirected &&
      response.headers.get('content-type')?.toLowerCase().includes('application/json') === true &&
      (cache?.toLowerCase().split(',').some(p => p.trim() === 'no-store') ?? false);
  }

  // A successful login rotates the session ID; never reuse a pre-login
  // CSRF token for subsequent privileged operations.
  async function freshCsrf(): Promise<string | null> {
    try {
      const response = await request('/api/v1/auth/csrf', { method: 'GET' });
      if (response.status !== 200 || !strictJson(response)) return null;
      const data: unknown = await response.json();
      if (!data || typeof data !== 'object' || Array.isArray(data)) return null;
      const token = (data as Record<string, unknown>).csrfToken;
      if (typeof token !== 'string' || token.length < 1 || token.length > 4096 ||
          token.trim() !== token) return null;
      return token;
    } catch { return null; }
  }

  async function session(): Promise<StagedSessionState> {
    try {
      const response = await request('/api/v1/auth/session', { method: 'GET' });
      if (response.status === 200 && strictJson(response)) {
        const data: unknown = await response.json();
        if (data && typeof data === 'object' && !Array.isArray(data) &&
            typeof (data as Record<string, unknown>).login === 'string') {
          const login = (data as { login: string }).login;
          if (login.length > 0 && login.length <= 255 && login.trim() === login) {
            return { kind: 'authenticated', login };
          }
        }
      }
      // Every unauthenticated, denied, throttled, malformed or offline
      // response drops all previously authorized query rows immediately.
      await erasePreviousAuthorizedRows();
      if (response.status === 401) return { kind: 'signed_out' };
      if (response.status === 403) return { kind: 'forbidden' };
      if (response.status === 429) return { kind: 'throttled' };
      return { kind: 'unavailable' };
    } catch {
      try { await erasePreviousAuthorizedRows(); } catch { /* fail closed */ }
      return { kind: 'unavailable' };
    }
  }

  async function login(username: string, password: string): Promise<StagedSessionState> {
    // A new identity must never inherit the previous administrator's rows.
    try { await erasePreviousAuthorizedRows(); }
    catch { return { kind: 'unavailable' }; }
    if (typeof username !== 'string' || username.length < 1 || username.length > 255 ||
        typeof password !== 'string' || password.length < 1 || password.length > 4096) {
      return { kind: 'unavailable' };
    }
    const token = await freshCsrf();
    if (!token) return { kind: 'unavailable' };
    try {
      const response = await request('/api/v1/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-CSRF-TOKEN': token,
        },
        body: new URLSearchParams({ username, password }),
      });
      if (response.status === 401) return { kind: 'invalid_credentials' };
      if (response.status === 403) return { kind: 'forbidden' };
      if (response.status === 429) return { kind: 'throttled' };
      if (response.status !== 204 || response.redirected) return { kind: 'unavailable' };
      // Id rotation is server controlled; require a NEW token and a fresh
      // authenticated 200 before ever describing the login as successful.
      if (!(await freshCsrf())) return { kind: 'unavailable' };
      return await session();
    } catch { return { kind: 'unavailable' }; }
  }

  async function logout(): Promise<StagedLogoutState> {
    try { await erasePreviousAuthorizedRows(); }
    catch { return 'unavailable'; }
    const token = await freshCsrf();
    if (!token) return 'unavailable';
    try {
      const response = await request('/api/v1/auth/logout', {
        method: 'POST',
        headers: { 'X-CSRF-TOKEN': token },
      });
      if (!response.redirected && response.status === 204) return 'signed_out';
      if (response.status === 403) return 'forbidden';
      return 'unavailable';
    } catch { return 'unavailable'; }
  }

  return { freshCsrf, session, login, logout };
}
