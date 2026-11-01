import { isCanonicalUuid } from './uuid';
import { projectConsoleList, type StagedConsoleResponse } from './console-device-projection';
import type { DirectoryState } from '../components/device-directory';

/**
 * NXT-03 request construction only. This module is NOT imported by any
 * Next.js page, route handler or server component. It must not be enabled
 * before PG18, Caddy TLS, real browser session/CSRF and RBAC acceptance.
 * Model types originate in Springdoc/Orval CI, never from this file.
 */
export type BrowserFetch = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function canonicalConsoleOrigin(configured: string): string {
  let url: URL;
  try { url = new URL(configured); }
  catch { throw new Error('Canonical HTTPS console origin required'); }
  const host = url.hostname;
  if (url.protocol !== 'https:' ||
      !/^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/.test(host) ||
      !host.includes('.') || /^[0-9.]+$/.test(host) ||
      configured !== `https://${host}` ||
      url.username || url.password || url.port ||
      url.pathname !== '/' || url.search || url.hash) {
    throw new Error('Canonical HTTPS console origin required');
  }
  return configured;
}

function hasNoStore(response: Response) {
  const cache = response.headers.get('cache-control');
  return typeof cache === 'string' &&
    cache.toLowerCase().split(',').some(x => x.trim() === 'no-store');
}

export function createStagedConsoleReader(
  expectedConsoleOrigin: string,
  currentBrowserOrigin: string,
  fetcher: BrowserFetch,
) {
  const origin = canonicalConsoleOrigin(expectedConsoleOrigin);
  if (canonicalConsoleOrigin(currentBrowserOrigin) !== origin) {
    // Do not allow a browser hosted on localhost, an untrusted Next host or
    // a second HTTPS subdomain to send even read-only credentials elsewhere.
    throw new Error('Console browser origin mismatch');
  }

  async function get(path: string, detail = false): Promise<StagedConsoleResponse> {
    try {
      const response = await fetcher(new URL(path, origin), {
        method: 'GET',
        headers: { Accept: 'application/json' },
        credentials: 'same-origin',
        mode: 'same-origin',
        cache: 'no-store',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
      });
      if (response.redirected) return { kind: 'unavailable' };
      if (response.status === 401) return { kind: 'signed_out' };
      if (response.status === 403) return { kind: 'forbidden' };
      if (response.status === 429) return { kind: 'throttled' };
      if (detail && response.status === 404) return { kind: 'not_found' };
      if (response.status !== 200 ||
          !response.headers.get('content-type')?.toLowerCase().includes('application/json') ||
          !hasNoStore(response)) return { kind: 'unavailable' };
      const data: unknown = await response.json();
      if (!data || typeof data !== 'object' || Array.isArray(data)) {
        return { kind: 'unavailable' };
      }
      return { kind: 'ok', data };
    } catch {
      return { kind: 'unavailable' };
    }
  }

  function list(options: Readonly<{ after?: string; limit?: number }> = {}):
    Promise<DirectoryState> {
    const limit = options.limit ?? 50;
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 100 ||
        (options.after !== undefined && !isCanonicalUuid(options.after))) {
      throw new TypeError('Invalid scoped directory page parameters');
    }
    const params = new URLSearchParams({ limit: String(limit) });
    if (options.after) params.set('cursor', options.after);
    return get(`/api/v1/devices?${params}`).then(result => projectConsoleList(result,options));
  }

  function detail(deviceId: string): Promise<StagedConsoleResponse> {
    if (!isCanonicalUuid(deviceId)) throw new TypeError('Invalid device ID');
    return get(`/api/v1/devices/${deviceId}`, true);
  }

  function overview(): Promise<StagedConsoleResponse> {
    return get('/api/v1/monitoring/overview');
  }

  return { list, detail, overview };
}
