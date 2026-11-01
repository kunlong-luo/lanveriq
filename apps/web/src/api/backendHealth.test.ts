import { describe, expect, it } from 'vitest';
import { probeBackendHealth, type HealthFetch } from './backendHealth';

const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: { 'content-type': 'application/json' },
});

describe('anonymous Spring Boot health only (synthetic HTTP responses)', () => {
  it('uses only one same-origin GET, without credentials, session or API business routes', async () => {
    const calls: Array<{ input: string; init?: RequestInit }> = [];
    const fetcher: HealthFetch = async (input, init) => {
      calls.push({ input: String(input), init });
      return json({ status: 'UP' });
    };
    await expect(probeBackendHealth(fetcher)).resolves.toBe('up');
    expect(calls).toHaveLength(1);
    expect(calls[0].input).toBe('/actuator/health');
    expect(calls[0].init).toMatchObject({
      method: 'GET', credentials: 'omit', mode: 'same-origin',
      redirect: 'error', cache: 'no-store', referrerPolicy: 'no-referrer',
    });
    expect(calls[0].init?.body).toBeUndefined();
  });

  it.each([
    ['health DOWN', json({ status: 'DOWN' })],
    ['no status', json({})],
    ['wrong status type', json({ status: true })],
    ['HTTP 401', json({ status: 'UP' }, 401)],
    ['HTTP 403', json({ status: 'UP' }, 403)],
    ['HTTP 503', json({ status: 'UP' }, 503)],
    ['wrong media type', new Response('{"status":"UP"}', {status: 200, headers: {'content-type': 'text/html'}})],
    ['unexpected HTML', new Response('<html>UP</html>', {status:200,headers:{'content-type':'application/json'}})],
    ['redirect', new Response(null, {status:302,headers:{location:'https://other.example.test'}})],
  ])('never declares backend healthy for %s', async (_label, response) => {
    await expect(probeBackendHealth(async () => response)).resolves.toBe('unavailable');
  });

  it('treats a failed fetch as unavailable without leaking a stack trace', async () => {
    await expect(probeBackendHealth(async () => {
      throw new Error('synthetic connection refused');
    })).resolves.toBe('unavailable');
  });
});
