'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Activity, LockKeyhole, LogOut, RefreshCcw } from 'lucide-react';
import { DeviceDirectory, type DirectoryState } from './components/device-directory';
import { Card, CardHeader, CardDescription, CardTitle } from './components/ui/card';
import { createStagedNextConsoleAuth } from './lib/staged-console-auth';
import { createStagedConsoleReader, type BrowserFetch } from './lib/staged-console-read';
import { createConsoleQueryClient, clearConsoleSessionState } from './lib/console-query-policy';
import { isConsistentScopedDirectoryPage, parseScopedConsoleOverview, type ScopedConsoleOverview } from './lib/staged-console-overview';

/**
 * NXT-08-A: MULTI-PAGE synthetic acceptance candidate, NOT production routing.
 *
 * This component is intentionally unreachable from committed src/app and
 * src/components. The fourth-gated disposable CI installer alone creates
 * three separate Next.js App Router pages referencing it.
 *
 * Each actual document navigation starts with an empty SSR shell and a
 * fresh Spring Session GET. No identity, device or metric is transferred via
 * React context, URLs, browser storage, RSC payloads or query cache.
 * The real Spring Security filter chain alone authorizes database responses.
 */
const CONSOLE = 'https://console.lanveriq.internal';
const ROOT = '/synthetic-console-route';
const views = [
  { id: 'overview', label: '概览' },
  { id: 'devices', label: '终端资产' },
  { id: 'monitoring', label: '监控中心' },
] as const;

export type SyntheticRouteView = (typeof views)[number]['id'];
type Status = 'checking' | 'working' | 'ready' | 'signed_out' | 'locked' |
  'forbidden' | 'throttled' | 'unavailable';
const emptyDirectory: DirectoryState = {kind: 'unconnected'};

export function StagedConsoleRouteBoundary({view}: Readonly<{view: SyntheticRouteView}>) {
  const [query] = useState(createConsoleQueryClient);
  const [status, setStatus] = useState<Status>('checking');
  const [principal, setPrincipal] = useState('');
  const [directory, setDirectory] = useState<DirectoryState>(emptyDirectory);
  const [overview, setOverview] = useState<ScopedConsoleOverview | null>(null);
  const username = useRef<HTMLInputElement>(null);
  const password = useRef<HTMLInputElement>(null);
  const epoch = useRef(0);
  const requestController = useRef<AbortController | null>(null);

  function erase() {
    query.clear();
    setPrincipal('');
    setDirectory(emptyDirectory);
    setOverview(null);
    if (username.current) username.current.value = '';
    if (password.current) password.current.value = '';
  }

  function clients(controller: AbortController) {
    const browserFetch: BrowserFetch = (input, init) =>
      window.fetch(input, {...init, signal: controller.signal});
    const sameOrigin = window.location.origin;
    return {
      auth: createStagedNextConsoleAuth(CONSOLE, sameOrigin, browserFetch,
        async () => {erase();}),
      reader: createStagedConsoleReader(CONSOLE, sameOrigin, browserFetch),
    };
  }

  function startOperation() {
    epoch.current += 1;
    requestController.current?.abort();
    const controller = new AbortController();
    requestController.current = controller;
    erase();
    return {token: epoch.current, controller};
  }

  function current(token: number, controller: AbortController) {
    return epoch.current === token && !controller.signal.aborted;
  }

  async function loadAuthorized(login: string, token: number, controller: AbortController) {
    const {reader} = clients(controller);
    const page = await reader.list();
    if (!current(token, controller)) return;
    if (page.kind !== 'ready') {
      erase();
      setStatus(page.kind === 'signed_out' || page.kind === 'forbidden' ||
        page.kind === 'throttled' ? page.kind : 'unavailable');
      return;
    }
    const response = await reader.overview();
    if (!current(token, controller)) return;
    const metrics = response.kind === 'ok' ?
      parseScopedConsoleOverview(response.data) : null;
    if (!metrics || !isConsistentScopedDirectoryPage(page, metrics)) {
      erase();
      setStatus(response.kind === 'signed_out' || response.kind === 'forbidden' ||
        response.kind === 'throttled' ? response.kind : 'unavailable');
      return;
    }
    setPrincipal(login);
    setDirectory(page);
    setOverview(metrics);
    setStatus('ready');
  }

  // Route entry is NOT an authority assertion. On every fresh URL navigation,
  // the client rechecks Spring's actual session before ever fetching devices.
  // This synthetic-only GET is intentionally different from the inert public
  // preview: no credential form is posted on mount.
  useEffect(() => {
    let active = true;
    const {token, controller} = startOperation();
    async function revalidate() {
      try {
        const session = await clients(controller).auth.session();
        if (!active || !current(token, controller)) return;
        if (session.kind !== 'authenticated') {
          erase();
          setStatus(session.kind === 'forbidden' || session.kind === 'throttled'
            ? session.kind : session.kind === 'signed_out'
              ? 'signed_out' : 'unavailable');
          return;
        }
        await loadAuthorized(session.login, token, controller);
      } catch {
        if (active && current(token, controller)) {
          erase();
          setStatus('unavailable');
        }
      }
    }
    void revalidate();
    const lock = () => {
      epoch.current += 1;
      controller.abort();
      requestController.current?.abort();
      erase();
      setStatus(old => old === 'ready' || old === 'working' ? 'locked' : old);
    };
    const visibility = () => {
      if (document.visibilityState === 'hidden') lock();
    };
    document.addEventListener('visibilitychange', visibility);
    window.addEventListener('pagehide', lock);
    return () => {
      active = false;
      document.removeEventListener('visibilitychange', visibility);
      window.removeEventListener('pagehide', lock);
      epoch.current += 1;
      controller.abort();
      requestController.current?.abort();
      query.clear();
    };
    // Each real route page mounts its own isolated component instance.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (status !== 'signed_out' && status !== 'unavailable') return;
    const name = username.current?.value ?? '';
    const secret = password.current?.value ?? '';
    const {token, controller} = startOperation();
    setStatus('working');
    try {
      const response = await clients(controller).auth.login(name, secret);
      if (!current(token, controller)) return;
      if (response.kind !== 'authenticated' || response.login !== name) {
        erase();
        setStatus(response.kind === 'forbidden' || response.kind === 'throttled'
          ? response.kind : response.kind === 'signed_out' ||
            response.kind === 'invalid_credentials' ? 'signed_out' : 'unavailable');
        return;
      }
      await loadAuthorized(name, token, controller);
    } catch {
      if (current(token, controller)) {erase(); setStatus('unavailable');}
    }
  }

  async function refresh() {
    if (status !== 'ready') return;
    const {token, controller} = startOperation();
    setStatus('working');
    try {
      const session = await clients(controller).auth.session();
      if (!current(token, controller)) return;
      if (session.kind !== 'authenticated') {
        erase();
        setStatus(session.kind === 'signed_out' ? 'signed_out' : 'unavailable');
        return;
      }
      await loadAuthorized(session.login, token, controller);
    } catch {
      if (current(token, controller)) {erase(); setStatus('unavailable');}
    }
  }

  async function logout() {
    if (status !== 'ready' && status !== 'locked') return;
    const {token, controller} = startOperation();
    setStatus('working');
    try {
      const result = await clients(controller).auth.logout();
      if (!current(token, controller)) return;
      erase();
      setStatus(result === 'signed_out' ? 'signed_out' : 'locked');
    } catch {
      if (current(token, controller)) {erase(); setStatus('locked');}
    }
  }

  const currentView = views.find(item => item.id === view)!;
  return <main className="page" data-route-view={view}
    data-route-state={status} data-route-principal={status === 'ready' ? principal : ''}>
    <div className="title-row">
      <div><p className="eyebrow">NXT-08 · DISPOSABLE MULTI-ROUTE SESSION GATE</p>
        <h1>{currentView.label} · 合成路由验收</h1>
        <p>每次真实 URL 导航均由 Spring 重新确认当前会话，不使用客户端身份缓存。</p>
      </div>
      <span className="backend-state"><LockKeyhole size={15}/> 临时验收环境</span>
    </div>
    {status === 'checking' || status === 'working' ? (
      <section role="status" aria-live="polite">正在重新验证合成管理员会话，不显示任何授权数据。</section>
    ) : status !== 'ready' ? (
      <section className="login-card">
        <div className="login-brand"><Activity size={22}/><strong>Lanveriq · 路由隔离</strong></div>
        <h2>{status === 'locked' ? '页面会话已锁定' : '合成会话尚未授权'}</h2>
        {status === 'locked' ? (
          <button className="primary-action" type="button" onClick={logout}>
            <LogOut size={16}/> 验证安全登出
          </button>
        ) : <form className="login-fields" onSubmit={login} autoComplete="off">
          <label htmlFor="route-user">合成管理员账号</label>
          <input id="route-user" ref={username} required autoComplete="off"/>
          <label htmlFor="route-pass">一次性合成密码</label>
          <input id="route-pass" ref={password} required type="password"
            autoComplete="new-password"/>
          <button className="primary-action" type="submit">仅在隔离环境登录</button>
        </form>}
        <p role="status" data-testid="route-auth-message">
          {status === 'signed_out' ? '未认证，不会展示设备或监控数据。' :
            status === 'locked' ? '页面隐藏后所有数据已清除，需要明确登出。' :
              status === 'forbidden' ? '访问被拒绝，没有授权数据。' :
                status === 'throttled' ? '请求受到限制，不会自动重试。' :
                  '会话无法安全确认，没有保留历史设备数据。'}
        </p>
      </section>
    ) : <section aria-label="当前会话已授权数据">
      <div className="title-row">
        <p data-testid="route-current-principal">当前合成账号：{principal}</p>
        <div className="top-actions">
          <button type="button" className="secondary-action" onClick={refresh}>
            <RefreshCcw size={16}/> 重新验证并刷新
          </button>
          <button type="button" className="secondary-action" onClick={logout}>
            <LogOut size={16}/> 安全登出
          </button>
        </div>
      </div>
      <nav className="staged-view-tabs" aria-label="受控多页面导航">
        {views.map(item => <a key={item.id} href={ROOT + '/' + item.id}
          className="secondary-action"
          aria-current={view === item.id ? 'page' : undefined}>{item.label}</a>)}
      </nav>
      {view !== 'devices' && overview && <div className="metric-grid">
        {([['已授权设备总数', overview.total], ['在线设备', overview.online],
          ['离线设备', overview.offline], ['待注册设备', overview.pending]] as const)
          .map(([label, value]) => <Card key={label} className="metric-card">
            <CardHeader><CardDescription>{label}</CardDescription>
              <CardTitle data-testid={'route-metric-' + label}>{value}</CardTitle>
            </CardHeader>
          </Card>)}
      </div>}
      {view !== 'monitoring' && <DeviceDirectory
        state={directory} allowDetailNavigation={false}/>}
      <p className="freshness-note">仅显示本次 URL 访问后重新验证的授权数据；
        全页跳转或会话失效会重新清空当前界面。</p>
    </section>}
  </main>;
}
