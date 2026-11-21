import { describe, expect, it } from 'vitest';
import { createStagedNextConsoleAuth } from './staged-console-auth';
import { createStagedConsoleReader, type BrowserFetch } from './staged-console-read';
import { clearConsoleSessionState, createConsoleQueryClient } from './console-query-policy';

const origin = 'https://console.lanveriq.internal';
const a = {
  device_id: '00000000-0000-7000-8000-000000000011',
  group_id: '00000000-0000-7000-8000-000000000101',
  label: 'Synthetic group A device',
  group_name: 'Synthetic group A',
  enrollment_state: 'active',
  heartbeat_received_at: null,
  queue_count: null,
};
const b = {
  device_id: '00000000-0000-7000-8000-000000000022',
  group_id: '00000000-0000-7000-8000-000000000202',
  label: 'Synthetic group B device',
  group_name: 'Synthetic group B',
  enrollment_state: 'active',
  heartbeat_received_at: null,
  queue_count: null,
};
const json = (data: unknown) => new Response(JSON.stringify(data), {
  status: 200,
  headers: {
    'content-type': 'application/json',
    'cache-control': 'private, no-store',
  },
});
const csrf = (token: string) => json({ csrfToken: token });

describe('NXT-05-B two-principal cache isolation (synthetic fetch only)', () => {
  it('drops group A records before group B login, forbids A detail in B scope, and clears revoked B data', async () => {
    const replies = [
      csrf('a-before-token'), new Response(null, { status: 204 }),
      csrf('a-rotated-token'), json({ login: 'synthetic-admin-a' }),
      json({ items: [a], next_cursor: null }),
      csrf('a-logout-token'), new Response(null, { status: 204 }),
      csrf('b-before-token'), new Response(null, { status: 204 }),
      csrf('b-rotated-token'), json({ login: 'synthetic-admin-b' }),
      json({ items: [b], next_cursor: null }),
      new Response(null, { status: 404 }),
      new Response(null, { status: 401 }),
    ];
    const calls: { url: string; init?: RequestInit }[] = [];
    const fetcher: BrowserFetch = async (input, init) => {
      calls.push({ url: String(input), init });
      const response = replies.shift();
      if (!response) throw Error('Unexpected synthetic request');
      return response;
    };
    const query = createConsoleQueryClient();
    const auth = createStagedNextConsoleAuth(
      origin, origin, fetcher, () => clearConsoleSessionState(query),
    );
    const read = createStagedConsoleReader(origin, origin, fetcher);

    expect(await auth.login('synthetic-admin-a', 'synthetic-only-a')).toEqual({
      kind: 'authenticated', login: 'synthetic-admin-a',
    });
    const first = await read.list();
    expect(first).toMatchObject({ kind: 'ready', rows: [{ id: a.device_id }] });
    query.setQueryData(['authorized', 'A', 'devices'], first);
    expect(query.getQueryCache().getAll()).toHaveLength(1);

    expect(await auth.logout()).toBe('signed_out');
    expect(query.getQueryCache().getAll()).toHaveLength(0);
    expect(await auth.login('synthetic-admin-b', 'synthetic-only-b')).toEqual({
      kind: 'authenticated', login: 'synthetic-admin-b',
    });
    expect(query.getQueryCache().getAll()).toHaveLength(0);

    const second = await read.list();
    expect(second).toMatchObject({ kind: 'ready', rows: [{ id: b.device_id }] });
    expect(second).not.toEqual(first);
    query.setQueryData(['authorized', 'B', 'devices'], second);
    expect(query.getQueryData(['authorized', 'A', 'devices'])).toBeUndefined();
    expect(await read.detail(a.device_id)).toEqual({ kind: 'not_found' });

    // Spring revokes B. The staged session transport wipes B rows before
    // returning 401 and never falls back to any stale A data.
    expect(await auth.session()).toEqual({ kind: 'signed_out' });
    expect(query.getQueryCache().getAll()).toHaveLength(0);
    expect(replies).toHaveLength(0);

    expect(calls.map(x => new URL(x.url).pathname)).toEqual([
      '/api/v1/auth/csrf', '/api/v1/auth/login',
      '/api/v1/auth/csrf', '/api/v1/auth/session',
      '/api/v1/devices',
      '/api/v1/auth/csrf', '/api/v1/auth/logout',
      '/api/v1/auth/csrf', '/api/v1/auth/login',
      '/api/v1/auth/csrf', '/api/v1/auth/session',
      '/api/v1/devices', '/api/v1/devices/' + a.device_id,
      '/api/v1/auth/session',
    ]);
    for (const sent of calls) {
      expect(sent.url.startsWith(origin + '/api/v1/')).toBe(true);
      expect(sent.url).not.toContain('synthetic-only-a');
      expect(sent.url).not.toContain('synthetic-only-b');
      expect(sent.init).toMatchObject({
        credentials: 'same-origin',
        mode: 'same-origin',
        cache: 'no-store',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
      });
      expect(new Headers(sent.init?.headers).has('Origin')).toBe(false);
    }
  });

  it('invalid or unavailable second administrator cannot revive first administrator data', async () => {
    for (const failedLogin of [401, 403, 429, 503]) {
      const replies = [
        csrf('a-before-token'), new Response(null, { status: 204 }),
        csrf('a-rotated-token'), json({ login: 'synthetic-admin-a' }),
        json({ items: [a], next_cursor: null }),
        csrf('a-logout-token'), new Response(null, { status: 204 }),
        csrf('b-before-token'), new Response(null, { status: failedLogin }),
      ];
      const fetcher: BrowserFetch = async () => {
        const response = replies.shift();
        if (!response) throw Error('Unexpected synthetic request');
        return response;
      };
      const query = createConsoleQueryClient();
      const auth = createStagedNextConsoleAuth(
        origin, origin, fetcher, () => clearConsoleSessionState(query),
      );
      const read = createStagedConsoleReader(origin, origin, fetcher);
      expect((await auth.login('synthetic-admin-a', 'test-only-a')).kind)
        .toBe('authenticated');
      query.setQueryData(['A'], await read.list());
      expect(query.getQueryCache().getAll()).toHaveLength(1);
      expect(await auth.logout()).toBe('signed_out');
      expect(query.getQueryCache().getAll()).toHaveLength(0);
      expect((await auth.login('synthetic-admin-b', 'wrong-password')).kind)
        .not.toBe('authenticated');
      expect(query.getQueryCache().getAll()).toHaveLength(0);
      expect(replies).toHaveLength(0);
    }
  });
});
