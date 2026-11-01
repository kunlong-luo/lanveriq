import { requireConsoleOrigin } from '../auth/consoleOrigin';
import type { ConsoleFetch } from '../auth/sessionTransport';

/**
 * FE-02-A staged READ-ONLY transport; intentionally NOT imported by App.tsx.
 * The actual response shape must come from the Springdoc/Orval generated
 * client, not from a duplicate handwritten DTO. Until PG18 + Caddy + browser
 * acceptance, do not wire this into Query hooks, login or the Vite proxy.
 */
export type ConsoleReadResult =
  | { kind: 'ok'; data: unknown }
  | { kind: 'signed_out' }
  | { kind: 'forbidden' }
  | { kind: 'not_found' }
  | { kind: 'throttled' }
  | { kind: 'unavailable' };

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function createConsoleReadTransport(httpsOrigin: string, fetcher: ConsoleFetch) {
  const origin = requireConsoleOrigin(httpsOrigin);

  async function read(path: string, allowNotFound = false): Promise<ConsoleReadResult> {
    // All paths are internally constructed; callers cannot supply arbitrary URLs.
    try {
      const response = await fetcher(new URL(path, origin), {
        method: 'GET',
        headers: { Accept: 'application/json' },
        credentials: 'same-origin',
        mode: 'same-origin',
        redirect: 'error',
        cache: 'no-store',
        referrerPolicy: 'no-referrer',
      });
      if (response.redirected) return { kind: 'unavailable' };
      if (response.status === 401) return { kind: 'signed_out' };
      if (response.status === 403) return { kind: 'forbidden' };
      if (response.status === 429) return { kind: 'throttled' };
      if (allowNotFound && response.status === 404) return { kind: 'not_found' };
      if (response.status !== 200 ||
          !response.headers.get('content-type')?.toLowerCase().includes('application/json') ||
          !response.headers.get('cache-control')?.toLowerCase().includes('no-store')) {
        return { kind: 'unavailable' };
      }
      const data: unknown = await response.json();
      if (!data || typeof data !== 'object' || Array.isArray(data)) {
        return { kind: 'unavailable' };
      }
      return { kind: 'ok', data };
    } catch {
      // Network failures and bad JSON never fall back to invented devices.
      return { kind: 'unavailable' };
    }
  }

  function listDevices(options: { cursor?: string; limit?: number } = {}) {
    const limit = options.limit ?? 50;
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
      throw new RangeError('Device page limit must be an integer from 1 to 100');
    }
    if (options.cursor !== undefined && !uuidPattern.test(options.cursor)) {
      throw new TypeError('Invalid device cursor');
    }
    const search = new URLSearchParams({ limit: String(limit) });
    if (options.cursor) search.set('cursor', options.cursor);
    return read(`/api/v1/devices?${search}`);
  }

  function findDevice(deviceId: string) {
    if (!uuidPattern.test(deviceId)) throw new TypeError('Invalid device ID');
    return read(`/api/v1/devices/${deviceId}`, true);
  }

  function monitoringOverview() {
    return read('/api/v1/monitoring/overview');
  }

  return { listDevices, findDevice, monitoringOverview };
}
