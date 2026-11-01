import { describe, expect, it } from 'vitest';
import { createConsoleReadTransport } from './consoleReadTransport';
import type { ConsoleFetch } from '../auth/sessionTransport';

const json = (data: unknown, cache = 'no-store') =>
  new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'content-type': 'application/json', 'cache-control': cache },
  });

function stub(responses: Array<Response | Error>) {
  const sent: Array<{ url: string; init?: RequestInit }> = [];
  const fetcher: ConsoleFetch = async (input, init) => {
    sent.push({ url: String(input), init });
    const response = responses.shift();
    if (!response || response instanceof Error) throw response ?? new Error('No response');
    return response;
  };
  return { sent, fetcher };
}

describe('FE-02-A staged read-only browser adapter (not wired to the UI)', () => {
  it('requires the same canonical DNS HTTPS origin as Spring ConsoleOriginPolicy', () => {
    const { fetcher, sent } = stub([]);
    for (const origin of [
      'http://console.example.test', 'https://localhost', 'https://127.0.0.1',
      'https://console.example.test/', 'https://console.example.test:443',
      'https://console.example.test:8443', 'https://user@console.example.test',
      'https://console.example.test/path', 'https://console.example.test?x=1',
      'https://console.example.test#fragment', 'https://CONSOLE.example.test',
    ]) {
      expect(() => createConsoleReadTransport(origin, fetcher)).toThrow();
    }
    expect(sent).toHaveLength(0);
  });

  it('lists a real empty page without inventing fallback devices or a cursor', async () => {
    const { fetcher, sent } = stub([json({ items: [], next_cursor: null })]);
    const client = createConsoleReadTransport('https://console.example.test', fetcher);
    await expect(client.listDevices()).resolves.toEqual({
      kind: 'ok', data: { items: [], next_cursor: null },
    });
    expect(sent).toHaveLength(1);
    expect(sent[0].url).toBe('https://console.example.test/api/v1/devices?limit=50');
    expect(sent[0].init).toMatchObject({
      method: 'GET', credentials: 'same-origin', mode: 'same-origin',
      redirect: 'error', cache: 'no-store', referrerPolicy: 'no-referrer',
      headers: { Accept: 'application/json' },
    });
    expect(new Headers(sent[0].init?.headers).has('Origin')).toBe(false);
    expect(sent[0].init?.body).toBeUndefined();
  });

  it('constructs only bounded scoped device and monitoring GET paths', async () => {
    const id = '00000000-0000-7000-8000-000000000001';
    const { fetcher, sent } = stub([json({items:[],next_cursor:null}),json({ device_id: id }),json({total:0})]);
    const client = createConsoleReadTransport('https://console.example.test', fetcher);
    await client.listDevices({ cursor:id, limit:100 });
    await client.findDevice(id);
    await client.monitoringOverview();
    expect(sent.map(call => new URL(call.url).pathname)).toEqual([
      '/api/v1/devices', `/api/v1/devices/${id}`, '/api/v1/monitoring/overview',
    ]);
    expect(new URL(sent[0].url).searchParams.get('cursor')).toBe(id);
    expect(new URL(sent[0].url).searchParams.get('limit')).toBe('100');
    for (const call of sent) {
      expect(new URL(call.url).origin).toBe('https://console.example.test');
      expect(call.init?.method).toBe('GET');
    }
  });

  it('rejects invalid device IDs and cursor/limit before any network request', () => {
    const { fetcher, sent } = stub([]);
    const client = createConsoleReadTransport('https://console.example.test', fetcher);
    for (const invalid of ['../auth/session', 'https://other.example/', 'not-a-uuid', '', '%2f']) {
      expect(() => client.findDevice(invalid)).toThrow();
      expect(() => client.listDevices({ cursor: invalid })).toThrow();
    }
    for (const limit of [0,101,-1,1.5,Number.NaN,Infinity]) {
      expect(() => client.listDevices({limit})).toThrow();
    }
    expect(sent).toHaveLength(0);
  });

  it.each([
    [401,'signed_out'], [403,'forbidden'], [429,'throttled'],
    [503,'unavailable'], [302,'unavailable'], [404,'unavailable'],
  ] as const)('fails closed on list HTTP %i without a demo fallback',async(status,kind) => {
    const { fetcher, sent } = stub([new Response(null, {status})]);
    await expect(createConsoleReadTransport('https://console.example.test',fetcher)
      .listDevices()).resolves.toEqual({kind});
    expect(sent).toHaveLength(1);
  });

  it('treats 404 as not_found only for one device', async () => {
    const { fetcher } = stub([new Response(null,{status:404})]);
    await expect(createConsoleReadTransport('https://console.example.test',fetcher)
      .findDevice('00000000-0000-7000-8000-000000000001'))
      .resolves.toEqual({kind:'not_found'});
  });

  it('rejects cacheable, non-JSON, malformed and inaccessible responses', async () => {
    for (const response of [
      json({items:[]},'public, max-age=60'),
      new Response('<html>login</html>', { status:200,headers:{'content-type':'text/html','cache-control':'no-store'} }),
      new Response('{bad-json', { status:200,headers:{'content-type':'application/json','cache-control':'no-store'} }),
      json(null), json([]), new Error('offline'),
    ]) {
      const { fetcher } = stub([response]);
      await expect(createConsoleReadTransport('https://console.example.test',fetcher)
        .listDevices()).resolves.toEqual({kind:'unavailable'});
    }
  });
});
