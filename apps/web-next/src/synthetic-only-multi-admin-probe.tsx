'use client';

import { useRef, useState, type FormEvent } from 'react';
import { createStagedNextConsoleAuth } from './lib/staged-console-auth';
import { createStagedConsoleReader, type BrowserFetch } from './lib/staged-console-read';
import { clearConsoleSessionState, createConsoleQueryClient } from './lib/console-query-policy';
import type { DirectoryRow } from './components/device-directory';

/** NEVER MOUNT IN A COMMITTED ROUTE. Disposable two-principal manual CI fixture.
 * This is not a production device listing or a user-facing login.
 */
const CONSOLE = 'https://console.lanveriq.internal';
const EXPECTED = {
  'synthetic-browser-a': { prefix: 'SyntheticBrowserA-', total: 2 },
  'synthetic-browser-b': { prefix: 'SyntheticBrowserB-', total: 1 },
} as const;

type ProbeState = 'idle' | 'working' | 'ready' | 'signed-out' |
  'scope-empty' | 'revoked' | 'denied';

export function SyntheticMultiAdminProbe() {
  const username = useRef<HTMLInputElement>(null);
  const password = useRef<HTMLInputElement>(null);
  const otherDevice = useRef<HTMLInputElement>(null);
  const [client] = useState(() => {
    const query = createConsoleQueryClient();
    // TEST ONLY: force a nonzero-lived scoped cache so this browser gate
    // actually proves principal switching and revocation wipe old rows.
    // The ordinary web preview keeps its separate gcTime=0 safety defaults.
    query.setQueryDefaults(['synthetic', 'authorized-devices'], {
      gcTime: 60_000,
    });
    return query;
  });
  const [state, setState] = useState<ProbeState>('idle');
  const [principal, setPrincipal] = useState('');
  const [rows, setRows] = useState<readonly DirectoryRow[]>([]);
  const [overviewTotal, setOverviewTotal] = useState<number | null>(null);
  const [detailState, setDetailState] = useState('untested');

  const browserFetch: BrowserFetch = (input, init) => window.fetch(input, init);
  const clients = () => {
    const currentOrigin = window.location.origin;
    return {
      auth: createStagedNextConsoleAuth(CONSOLE, currentOrigin, browserFetch,
        () => clearConsoleSessionState(client)),
      reader: createStagedConsoleReader(CONSOLE, currentOrigin, browserFetch),
    };
  };
  async function erase() {
    await clearConsoleSessionState(client);
    setRows([]);
    setOverviewTotal(null);
    setDetailState('untested');
  }

  async function fetchScoped(expectedLogin: string) {
    const { reader } = clients();
    const list = await reader.list();
    if (list.kind === 'signed_out') {
      await erase();
      setPrincipal('');
      setState('revoked');
      return;
    }
    if (list.kind !== 'ready') {
      await erase();
      setState('denied');
      return;
    }
    if (list.rows.length === 0) {
      await erase();
      setState('scope-empty');
      return;
    }
    const expectation = EXPECTED[expectedLogin as keyof typeof EXPECTED];
    const overview = await reader.overview();
    const rawTotal = overview.kind === 'ok' &&
      overview.data !== null && typeof overview.data === 'object' &&
      !Array.isArray(overview.data)
      ? (overview.data as Record<string, unknown>).total : null;
    const total = typeof rawTotal === 'number' &&
      Number.isSafeInteger(rawTotal) ? rawTotal : null;
    if (!expectation || total !== expectation.total ||
        list.rows.length !== expectation.total ||
        list.rows.some(row => !row.label.startsWith(expectation.prefix))) {
      await erase();
      setState('denied');
      return;
    }
    // Explicit non-persistent, session-scoped cache. Old rows are wiped before
    // login, logout, scope loss, account disable and each refetch.
    await erase();
    client.setQueryData(['synthetic', 'authorized-devices'], list);
    setRows(list.rows);
    setOverviewTotal(total);
    setState('ready');
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (state === 'working') return;
    const identity = username.current?.value ?? '';
    const secret = password.current?.value ?? '';
    if (username.current) username.current.value = '';
    if (password.current) password.current.value = '';
    await erase();
    setPrincipal('');
    setState('working');
    try {
      const { auth } = clients();
      const result = await auth.login(identity, secret);
      if (result.kind !== 'authenticated' || result.login !== identity ||
          !(identity in EXPECTED)) {
        await erase();
        setState('denied');
        return;
      }
      setPrincipal(identity);
      await fetchScoped(identity);
    } catch {
      await erase();
      setState('denied');
    }
  }

  async function refresh() {
    if (state !== 'ready' && state !== 'scope-empty') return;
    setState('working');
    try { await fetchScoped(principal); }
    catch { await erase(); setState('denied'); }
  }

  async function inspectOther() {
    if (state !== 'ready') return;
    const candidate = otherDevice.current?.value ?? '';
    if (otherDevice.current) otherDevice.current.value = '';
    setDetailState('working');
    try {
      const { reader } = clients();
      const result = await reader.detail(candidate);
      setDetailState(result.kind);
    } catch { setDetailState('unavailable'); }
  }

  async function logout() {
    if (state !== 'ready') return;
    setState('working');
    try {
      const { auth, reader } = clients();
      const outcome = await auth.logout();
      const session = await auth.session();
      const list = await reader.list();
      await erase();
      setPrincipal('');
      setState(outcome === 'signed_out' && session.kind === 'signed_out' &&
        list.kind === 'signed_out' && client.getQueryCache().getAll().length === 0
        ? 'signed-out' : 'denied');
    } catch { await erase(); setState('denied'); }
  }

  return <main style={{maxWidth: 520, margin: '3rem auto', fontFamily: 'system-ui'}}>
    <h1>双管理员合成隔离测试</h1>
    <p>临时 PostgreSQL 18 / Chromium 验收专用，禁止真实账号</p>
    <form onSubmit={login} autoComplete="off">
      <label htmlFor="multi-admin-user">合成账号</label>
      <input id="multi-admin-user" ref={username} required autoComplete="off"/>
      <label htmlFor="multi-admin-secret">一次性合成密码</label>
      <input id="multi-admin-secret" ref={password} required type="password"
             autoComplete="new-password"/>
      <button type="submit" disabled={state === 'working'}>测试登录并读取</button>
    </form>
    <button type="button" onClick={refresh}
            disabled={state !== 'ready' && state !== 'scope-empty'}>
      刷新已授权设备
    </button>
    <button type="button" onClick={logout} disabled={state !== 'ready'}>
      测试登出
    </button>
    <label htmlFor="multi-other-device">跨组详情设备 UUID</label>
    <input id="multi-other-device" ref={otherDevice} autoComplete="off"/>
    <button type="button" onClick={inspectOther} disabled={state !== 'ready'}>
      检查跨组详情
    </button>
    <p id="multi-admin-state" data-state={state} data-principal={principal}
       data-cache-count={client.getQueryCache().getAll().length}
       role="status">{state}</p>
    <p id="multi-admin-overview" data-total={overviewTotal ?? -1}>当前组总计：{overviewTotal ?? '无'}</p>
    <p id="multi-admin-detail" data-state={detailState}>详情检查：{detailState}</p>
    <ul id="multi-admin-device-list">
      {rows.map(row => <li key={row.id} data-device-id={row.id}>{row.label}</li>)}
    </ul>
  </main>;
}
