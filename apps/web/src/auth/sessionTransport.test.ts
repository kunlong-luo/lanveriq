import { describe, expect, it } from 'vitest';
import { createSessionTransport, type ConsoleFetch } from './sessionTransport';

const json = (data: unknown, status = 200, cache = 'no-store') =>
  new Response(JSON.stringify(data), {
    status, headers: { 'content-type': 'application/json', 'cache-control': cache },
  });
const csrf = (token = 'synthetic-csrf-token') => json({ csrfToken: token });

function fakeResponses(responses: Array<Response | Error>) {
  const sent: Array<{ url: string; init: RequestInit | undefined }> = [];
  const fetcher: ConsoleFetch = async (input, init) => {
    sent.push({ url: String(input), init });
    const result = responses.shift();
    if (!result || result instanceof Error) throw result ?? new Error('No stubbed network response');
    return result;
  };
  return { fetcher, sent };
}

describe('staged same-origin auth transport: no real browser networking', () => {
  it('rejects HTTP, noncanonical origins, userinfo and cross-origin path injection', () => {
    const blocked = ['http://console.example.test', 'https://console.example.test/',
      'https://user@console.example.test', 'https://console.example.test/path',
      'https://console.example.test?x=1', 'https://console.example.test:443',
      'https://console.example.test#fragment'];
    const { fetcher, sent } = fakeResponses([]);
    for (const origin of blocked) {
      expect(() => createSessionTransport(origin, fetcher)).toThrow();
    }
    expect(sent).toHaveLength(0);
  });

  it('uses CSRF -> form login -> FRESH CSRF -> session, never stores tokens or passwords', async () => {
    const { fetcher, sent } = fakeResponses([
      csrf('token-before-login'), new Response(null, { status: 204 }),
      csrf('token-after-fixation'), json({ login: 'synthetic.owner' }),
    ]);
    const client = createSessionTransport('https://console.example.test', fetcher);
    await expect(client.login('synthetic.owner', 'synth&test=secret?')).resolves.toEqual({
      kind: 'authenticated', login: 'synthetic.owner',
    });
    expect(sent.map(item => new URL(item.url).pathname)).toEqual([
      '/api/v1/auth/csrf', '/api/v1/auth/login',
      '/api/v1/auth/csrf', '/api/v1/auth/session',
    ]);
    for (const { url, init } of sent) {
      expect(new URL(url).origin).toBe('https://console.example.test');
      expect(init).toMatchObject({
        credentials: 'same-origin', mode: 'same-origin',
        cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      });
      expect(url).not.toContain('secret');
      expect(url).not.toContain('token-');
      expect(new Headers(init?.headers).has('Origin')).toBe(false); // browser, not JS, supplies Origin
    }
    const form = sent[1].init!;
    expect(form.method).toBe('POST');
    expect(form.headers).toMatchObject({
      'Content-Type': 'application/x-www-form-urlencoded',
      'X-CSRF-TOKEN': 'token-before-login',
    });
    expect(form.body).toBeInstanceOf(URLSearchParams);
    expect(new URLSearchParams(form.body as URLSearchParams).get('password'))
      .toBe('synth&test=secret?');
  });

  it('never makes a login POST if the CSRF bootstrap is missing or cacheable', async () => {
    for (const first of [
      json({}, 200), json({ csrfToken: '' }), json({ csrfToken: 'bad' }, 200, 'public'),
      new Response('', { status: 403 }),
      new Error('synthetic offline'),
    ]) {
      const { fetcher, sent } = fakeResponses([first]);
      const result = await createSessionTransport('https://console.example.test', fetcher)
        .login('synthetic', 'not-real');
      expect(result).toEqual({ kind: 'unavailable' });
      expect(sent).toHaveLength(1);
      expect(sent[0].init?.method).not.toBe('POST');
    }
  });

  it.each([
    [401, 'invalid_credentials'],
    [403, 'forbidden'],
    [429, 'throttled'],
    [503, 'unavailable'],
    [302, 'unavailable'],
  ] as const)('rejects HTTP %i login without constructing an authenticated session', async (status, kind) => {
    const { fetcher, sent } = fakeResponses([
      csrf(), new Response(null, { status, headers: {location:'https://other.example.test'} }),
    ]);
    await expect(createSessionTransport('https://console.example.test', fetcher)
      .login('synthetic', 'not-real')).resolves.toEqual({ kind });
    expect(sent).toHaveLength(2);
  });

  it('fails closed when token/session cannot be re-established after 204', async () => {
    const { fetcher, sent } = fakeResponses([
      csrf(), new Response(null, { status: 204 }), new Response(null, { status: 401 }),
    ]);
    await expect(createSessionTransport('https://console.example.test', fetcher)
      .login('synthetic', 'not-real')).resolves.toEqual({ kind:'unavailable' });
    expect(sent).toHaveLength(3);
    const second = fakeResponses([csrf(), new Response(null, {status:204}), csrf(), new Response(null, {status:401})]);
    await expect(createSessionTransport('https://console.example.test', second.fetcher)
      .login('synthetic', 'not-real')).resolves.toEqual({ kind:'signed_out' });
  });

  it('treats 401 and 403 session reads as not signed in / unauthorized', async () => {
    for (const [status, kind] of [[401,'signed_out'], [403,'forbidden']] as const) {
      const { fetcher } = fakeResponses([new Response(null,{status})]);
      await expect(createSessionTransport('https://console.example.test',fetcher)
        .session()).resolves.toEqual({ kind });
    }
    const { fetcher } = fakeResponses([json({login:'owner'},200,'max-age=3600')]);
    await expect(createSessionTransport('https://console.example.test',fetcher)
      .session()).resolves.toEqual({kind:'unavailable'});
  });

  it('always obtains a new CSRF token to logout, using the same-origin session cookie', async () => {
    const { fetcher, sent } = fakeResponses([csrf('logout-token'),new Response(null,{status:204})]);
    await expect(createSessionTransport('https://console.example.test',fetcher)
      .logout()).resolves.toBe('signed_out');
    expect(sent.map(x => new URL(x.url).pathname)).toEqual([
      '/api/v1/auth/csrf','/api/v1/auth/logout',
    ]);
    expect(sent[1].init?.headers).toMatchObject({'X-CSRF-TOKEN':'logout-token'});
    expect(sent[1].init?.credentials).toBe('same-origin');
    expect(sent[1].init?.method).toBe('POST');
  });

  it('does not submit logout when CSRF is unavailable', async () => {
    const { fetcher, sent } = fakeResponses([new Response(null,{status:401})]);
    await expect(createSessionTransport('https://console.example.test',fetcher)
      .logout()).resolves.toBe('unavailable');
    expect(sent).toHaveLength(1);
  });
});
