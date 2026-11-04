'use client';

import { useRef, useState, type FormEvent } from 'react';
import { createStagedNextConsoleAuth } from './lib/staged-console-auth';
import { createStagedConsoleReader, type BrowserFetch } from './lib/staged-console-read';
import { clearConsoleSessionState, createConsoleQueryClient } from './lib/console-query-policy';

/**
 * MANUAL, DISPOSABLE TEST FIXTURE ONLY. Never import from a committed route,
 * component, layout, production login page or ordinary preview.
 *
 * The opt-in GitHub job temporarily writes a separate App Router page which
 * mounts this fixture for one isolated Caddy/PG18/Chromium run. Credentials
 * originate in the runner's one-use synthetic bootstrap and stay in a browser
 * password input for the duration of that single click; they are not stored
 * in React state, URL, logging, localStorage or the Next.js server.
 */
const CONSOLE = 'https://console.lanveriq.internal';

type ProbeState = 'idle' | 'working' | 'authenticated-empty' |
  'signed-out-confirmed' | 'denied';

export function SyntheticOnlyAuthProbe() {
  const username = useRef<HTMLInputElement>(null);
  const password = useRef<HTMLInputElement>(null);
  const [state, setState] = useState<ProbeState>('idle');
  const [queryClient] = useState(createConsoleQueryClient);

  const browserFetch: BrowserFetch = (input, init) => window.fetch(input, init);

  function clients() {
    // The staged modules check this exact canonical HTTPS origin before ANY
    // fetch. This fixture cannot make localhost or cross-vhost login requests.
    const actualOrigin = window.location.origin;
    return {
      auth: createStagedNextConsoleAuth(
        CONSOLE, actualOrigin, browserFetch,
        () => clearConsoleSessionState(queryClient),
      ),
      reader: createStagedConsoleReader(CONSOLE, actualOrigin, browserFetch),
    };
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (state === 'working') return;
    const loginName = username.current?.value ?? '';
    const secret = password.current?.value ?? '';
    // Clear DOM fields before network awaits, and never hold passwords in
    // component state, errors, query caches, links or telemetry.
    if (username.current) username.current.value = '';
    if (password.current) password.current.value = '';
    setState('working');
    try {
      const { auth, reader } = clients();
      const result = await auth.login(loginName, secret);
      if (result.kind !== 'authenticated' || result.login !== 'synthetic-owner') {
        setState('denied');
        return;
      }
      const rows = await reader.list();
      const overview = await reader.overview();
      // The first stage tests an actual credentialed browser read against an
      // EMPTY disposable PG18 fixture. The separate nonempty multi-group
      // Servlet/JPA gate is not conflated with this UI behavior.
      const totals = overview.kind === 'ok' ? overview.data : null;
      if (rows.kind === 'ready' && rows.rows.length === 0 &&
          !rows.hasMore && totals !== null && typeof totals === 'object' &&
          !Array.isArray(totals) &&
          (totals as Record<string, unknown>).total === 0) {
        setState('authenticated-empty');
      } else {
        await clearConsoleSessionState(queryClient);
        setState('denied');
      }
    } catch {
      await clearConsoleSessionState(queryClient);
      setState('denied');
    }
  }

  async function logout() {
    if (state !== 'authenticated-empty') return;
    setState('working');
    try {
      const { auth, reader } = clients();
      const result = await auth.logout();
      const session = await auth.session();
      const rows = await reader.list();
      if (result === 'signed_out' && session.kind === 'signed_out' &&
          rows.kind === 'signed_out' && queryClient.getQueryCache().getAll().length === 0) {
        setState('signed-out-confirmed');
      } else {
        await clearConsoleSessionState(queryClient);
        setState('denied');
      }
    } catch {
      await clearConsoleSessionState(queryClient);
      setState('denied');
    }
  }

  return <main style={{maxWidth: 480, margin: '5rem auto', fontFamily: 'system-ui'}}>
    <h1>合成登录浏览器联调</h1>
    <p>仅一次性测试身份 · 禁止输入真实管理员凭据</p>
    <form onSubmit={login} autoComplete="off">
      <label htmlFor="synthetic-auth-user">测试管理员</label>
      <input id="synthetic-auth-user" ref={username} type="text"
             autoComplete="off" required maxLength={255}/>
      <label htmlFor="synthetic-auth-password">一次性测试密码</label>
      <input id="synthetic-auth-password" ref={password} type="password"
             autoComplete="new-password" required maxLength={4096}/>
      <button type="submit" disabled={state === 'working'}>合成登录并读取</button>
    </form>
    <button type="button" onClick={logout}
            disabled={state !== 'authenticated-empty'}>合成登出并复核</button>
    <p id="synthetic-auth-state" role="status" data-state={state}>{state}</p>
  </main>;
}
