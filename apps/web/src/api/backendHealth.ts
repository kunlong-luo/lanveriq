/**
 * WEB-01-HEALTH: ONLY public, anonymous Spring Boot health status.
 * This is not device data, user authentication, PostgreSQL access or WSS.
 * In Vite development, the exact path is proxied to a loopback Java port.
 */
export type BackendHealth = 'up' | 'unavailable';

export type HealthFetch = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export async function probeBackendHealth(
  fetcher: HealthFetch = fetch,
  signal?: AbortSignal,
): Promise<BackendHealth> {
  try {
    const response = await fetcher('/actuator/health', {
      method: 'GET',
      credentials: 'omit',
      mode: 'same-origin',
      redirect: 'error',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      headers: { Accept: 'application/json' },
      signal,
    });
    if (response.status !== 200 || response.redirected ||
        !response.headers.get('content-type')?.toLowerCase().includes('application/json')) {
      return 'unavailable';
    }
    const data: unknown = await response.json();
    if (!data || typeof data !== 'object' || Array.isArray(data) ||
        !('status' in data) || data.status !== 'UP') {
      return 'unavailable';
    }
    return 'up';
  } catch {
    // A down/unreachable backend is NOT proof that Redis or PostgreSQL failed.
    return 'unavailable';
  }
}
