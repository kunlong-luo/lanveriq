import { describe, expect, it } from 'vitest';
import { createStagedNextConsoleAuth } from './staged-console-auth';
import { clearConsoleSessionState, createConsoleQueryClient } from './console-query-policy';
import type { BrowserFetch } from './staged-console-read';

const origin = 'https://console.lanveriq.internal';
const json = (data: unknown, status = 200, cache = 'private, no-store') =>
  new Response(JSON.stringify(data), {
    status, headers: { 'content-type': 'application/json', 'cache-control': cache },
  });
const csrf = (value = 'synthetic-csrf') => json({ csrfToken: value });

function stub(responses: (Response | Error)[]) {
  const sent: { url: string; init?: RequestInit }[] = [];
  const fetcher: BrowserFetch = async (input, init) => {
    sent.push({ url: String(input), init });
    const r = responses.shift();
    if (r instanceof Error || !r) throw r ?? new Error('No synthetic response');
    return r;
  };
  return { sent, fetcher };
}
function auth(responses: (Response | Error)[], erase = async () => {}) {
  const fake = stub(responses);
  return { ...fake, client: createStagedNextConsoleAuth(origin, origin, fake.fetcher, erase) };
}

describe('staged Next.js admin session transport (synthetic fetch, never mounted)', () => {
  it('rejects every untrusted browser origin before network', () => {
    const { sent, fetcher } = stub([]);
    for (const browser of [
      'http://localhost:3100', 'http://console.lanveriq.internal',
      'https://enroll.lanveriq.internal', 'https://console.lanveriq.internal/',
      'https://console.lanveriq.internal:443', 'https://user@console.lanveriq.internal',
      'https://CONSOLE.lanveriq.internal',
    ]) {
      expect(() => createStagedNextConsoleAuth(origin, browser, fetcher, async () => {})).toThrow();
    }
    expect(sent).toHaveLength(0);
  });

  it('performs CSRF -> POST form -> NEW CSRF -> authenticated session, no credential URL', async () => {
    const q = createConsoleQueryClient();
    const key = ['admin', 'previous', 'devices'];
    q.setQueryData(key, [{ id: 'synthetic-previous-owner' }]);
    const { client, sent } = auth([
      csrf('before'), new Response(null, { status: 204 }),
      csrf('after'), json({ login: 'synthetic-owner' }),
    ], () => clearConsoleSessionState(q));
    await expect(client.login('synthetic-owner', 'not-real&=password')).resolves.toEqual({
      kind: 'authenticated', login: 'synthetic-owner',
    });
    expect(q.getQueryData(key)).toBeUndefined();
    expect(sent.map(x => new URL(x.url).pathname)).toEqual([
      '/api/v1/auth/csrf', '/api/v1/auth/login',
      '/api/v1/auth/csrf', '/api/v1/auth/session',
    ]);
    for (const { url, init } of sent) {
      expect(url.startsWith(origin + '/api/v1/auth/')).toBe(true);
      expect(url).not.toContain('password');
      expect(url).not.toContain('before');
      expect(init).toMatchObject({
        credentials: 'same-origin', mode: 'same-origin',
        cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      });
      expect(new Headers(init?.headers).has('Origin')).toBe(false);
    }
    expect(sent[1].init).toMatchObject({
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': 'before' },
    });
    expect(sent[1].init?.body).toBeInstanceOf(URLSearchParams);
    expect((sent[1].init?.body as URLSearchParams).get('password')).toBe('not-real&=password');
  });

  it('never sends password if csrf is unavailable, malformed or cacheable', async () => {
    for (const r of [
      json({}), json({ csrfToken: '' }), json({ csrfToken: 'bad' }, 200, 'public'),
      json({ csrfToken: 'bad' }, 200, 'no-store-bad'),
      new Response(null, { status: 403 }), new Error('offline'),
    ]) {
      const { client, sent } = auth([r]);
      await expect(client.login('synthetic', 'fake-secret')).resolves.toEqual({ kind: 'unavailable' });
      expect(sent).toHaveLength(1);
      expect(sent[0].init?.method).toBe('GET');
    }
  });

  it.each([
    [401, 'invalid_credentials'], [403, 'forbidden'], [429, 'throttled'],
    [503, 'unavailable'], [302, 'unavailable'],
  ] as const)('does not authenticate on login HTTP %i', async (code, kind) => {
    const { client, sent } = auth([csrf(), new Response(null, { status: code })]);
    await expect(client.login('fake', 'fake')).resolves.toEqual({ kind });
    expect(sent).toHaveLength(2);
  });

  it('204 alone cannot authenticate without post-fixation fresh csrf and session', async () => {
    for (const tail of [
      [new Response(null, { status: 401 })],
      [csrf(), new Response(null, { status: 401 })],
      [csrf(), json({ login: '' })],
    ]) {
      const { client } = auth([csrf(), new Response(null, { status: 204 }), ...tail]);
      expect((await client.login('fake', 'fake')).kind).not.toBe('authenticated');
    }
  });

  it('drops old query rows on every non-authenticated session result', async () => {
    for (const response of [
      new Response(null, { status: 401 }), new Response(null, { status: 403 }),
      new Response(null, { status: 429 }), new Response(null, { status: 503 }),
      json({ login: '' }), json({ login: 'fake' }, 200, 'public'), new Error('offline'),
    ]) {
      const q = createConsoleQueryClient();
      q.setQueryData(['console', 'rows'], ['sensitive-old-row']);
      const { client } = auth([response], () => clearConsoleSessionState(q));
      expect((await client.session()).kind).not.toBe('authenticated');
      expect(q.getQueryCache().getAll()).toHaveLength(0);
    }
  });

  it('erases previous authorization before logout; only exact csrf can submit', async () => {
    const q = createConsoleQueryClient();
    q.setQueryData(['console', 'rows'], ['previous']);
    const { client, sent } = auth([csrf('logout-only'), new Response(null, { status: 204 })],
      () => clearConsoleSessionState(q));
    await expect(client.logout()).resolves.toBe('signed_out');
    expect(q.getQueryCache().getAll()).toHaveLength(0);
    expect(sent.map(x => new URL(x.url).pathname)).toEqual([
      '/api/v1/auth/csrf', '/api/v1/auth/logout',
    ]);
    expect(sent[1].init?.method).toBe('POST');
    expect(sent[1].init?.headers).toMatchObject({ 'X-CSRF-TOKEN': 'logout-only' });
    const invalid = auth([new Response(null, { status: 401 })]);
    await expect(invalid.client.logout()).resolves.toBe('unavailable');
    expect(invalid.sent).toHaveLength(1);
  });
});
