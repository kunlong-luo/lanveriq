'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Activity, ArrowRight, LockKeyhole, LogOut, RefreshCcw } from 'lucide-react';
import { DeviceDirectory, type DirectoryState } from './components/device-directory';
import { Card, CardHeader, CardDescription, CardTitle } from './components/ui/card';
import { createStagedNextConsoleAuth } from './lib/staged-console-auth';
import { createStagedConsoleReader, type BrowserFetch } from './lib/staged-console-read';
import { createConsoleQueryClient, clearConsoleSessionState } from './lib/console-query-policy';
import { parseScopedConsoleOverview, type ScopedConsoleOverview } from './lib/staged-console-overview';

/**
 * NXT-06-A: the PRODUCT-SHAPED read-only Console candidate.
 *
 * IMPORTANT: this module MUST stay unmounted from all committed App Router
 * pages, components and route handlers. A test-only installer mounts it
 * temporarily only after the four synthetic GitHub acceptance gates are
 * explicitly requested (or selected by filtered main-push CI). Production
 * /login, /overview, /devices and /monitoring remain disabled/inert.
 *
 * No automatic network effects, session restoration, interval polling,
 * external proxy, localStorage, retry, Agent request or employee collection.
 * The only effect listens for page visibility/pagehide to clear state.
 * Every request originates from a deliberate user-like button/form action.
 */
const ORIGIN = 'https://console.lanveriq.internal';

type Status = 'signed_out' | 'working' | 'ready' | 'locked' |
  'invalid_credentials' | 'forbidden' | 'throttled' | 'unavailable';

const initialDirectory: DirectoryState = { kind: 'unconnected' };

export function StagedConsoleWorkspace() {
  const username = useRef<HTMLInputElement>(null);
  const password = useRef<HTMLInputElement>(null);
  const [client] = useState(createConsoleQueryClient);
  // A stale in-flight login/read must never re-populate rows after the
  // operator navigates away or the tab goes into the background.
  const operationEpoch = useRef(0);
  const [status, setStatus] = useState<Status>('signed_out');
  const [principal, setPrincipal] = useState('');
  const [directory, setDirectory] = useState<DirectoryState>(initialDirectory);
  const [overview, setOverview] = useState<ScopedConsoleOverview | null>(null);

  // DOM event handling is local-only. No automatic API request, session
  // restoration, credential submission, polling or background collection.
  // A hidden/restored tab is locked until Spring confirms explicit logout.
  useEffect(() => {
    const lockOnLeave = () => {
      operationEpoch.current += 1;
      // pagehide may terminate JavaScript before an awaited query cancel.
      // Drop the in-memory cache synchronously, then cancel as best effort.
      client.clear();
      void clearConsoleSessionState(client).catch(() => client.clear());
      setPrincipal('');
      setDirectory(initialDirectory);
      setOverview(null);
      setStatus(previous =>
        previous === 'ready' || previous === 'working' ? 'locked' : previous);
    };
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') lockOnLeave();
    };
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('pagehide', lockOnLeave);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('pagehide', lockOnLeave);
      operationEpoch.current += 1;
      client.clear();
      void clearConsoleSessionState(client).catch(() => client.clear());
    };
  }, [client]);

  const browserFetch: BrowserFetch = (input, init) => window.fetch(input, init);

  async function eraseAuthorizedData() {
    await clearConsoleSessionState(client);
    setPrincipal('');
    setDirectory(initialDirectory);
    setOverview(null);
  }

  function clients() {
    const actualOrigin = window.location.origin;
    return {
      auth: createStagedNextConsoleAuth(ORIGIN, actualOrigin, browserFetch,
        eraseAuthorizedData),
      reader: createStagedConsoleReader(ORIGIN, actualOrigin, browserFetch),
    };
  }

  async function fetchAuthorized(login: string, epoch: number): Promise<void> {
    const { reader } = clients();
    const page = await reader.list();
    if (epoch !== operationEpoch.current) return;
    if (page.kind !== 'ready') {
      await eraseAuthorizedData();
      setStatus(page.kind === 'loading' || page.kind === 'unconnected'
        ? 'unavailable' : page.kind === 'signed_out'
        ? 'signed_out' : page.kind);
      return;
    }
    const result = await reader.overview();
    if (epoch !== operationEpoch.current) return;
    const parsed = result.kind === 'ok'
      ? parseScopedConsoleOverview(result.data) : null;
    // Two independent HTTP reads can straddle a scope-revocation commit.
    // If the server's final total is lower than the earlier visible page,
    // fail closed rather than rendering a known-inconsistent snapshot.
    if (!parsed || parsed.total < page.rows.length) {
      await eraseAuthorizedData();
      setStatus(result.kind === 'signed_out' ? 'signed_out'
        : result.kind === 'forbidden' ? 'forbidden'
        : result.kind === 'throttled' ? 'throttled' : 'unavailable');
      return;
    }
    // Group grants and monitoring counters come from the server's CURRENT
    // principal. Never manufacture totals from a partial/paginated page.
    setPrincipal(login);
    setDirectory(page);
    setOverview(parsed);
    setStatus('ready');
  }

  async function onLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (status === 'working' || status === 'locked' || status === 'ready') return;
    const epoch = ++operationEpoch.current;
    const loginName = username.current?.value ?? '';
    const secret = password.current?.value ?? '';
    // No credentials in React state, query cache, URL, diagnostics or logs.
    if (username.current) username.current.value = '';
    if (password.current) password.current.value = '';
    await eraseAuthorizedData();
    if (epoch !== operationEpoch.current) return;
    setStatus('working');
    try {
      const { auth } = clients();
      const response = await auth.login(loginName, secret);
      if (epoch !== operationEpoch.current) return;
      if (response.kind !== 'authenticated' || response.login !== loginName) {
        await eraseAuthorizedData();
        if (epoch !== operationEpoch.current) return;
        setStatus(response.kind === 'invalid_credentials' ? 'invalid_credentials'
          : response.kind === 'forbidden' ? 'forbidden'
          : response.kind === 'throttled' ? 'throttled' : 'unavailable');
        return;
      }
      await fetchAuthorized(loginName, epoch);
    } catch {
      if (epoch !== operationEpoch.current) return;
      await eraseAuthorizedData();
      if (epoch === operationEpoch.current) setStatus('unavailable');
    }
  }

  async function refresh() {
    if (status !== 'ready') return;
    const epoch = ++operationEpoch.current;
    const login = principal;
    await eraseAuthorizedData();
    if (epoch !== operationEpoch.current) return;
    setStatus('working');
    try {
      await fetchAuthorized(login, epoch);
    } catch {
      if (epoch !== operationEpoch.current) return;
      await eraseAuthorizedData();
      if (epoch === operationEpoch.current) setStatus('unavailable');
    }
  }

  async function logout() {
    if (status !== 'ready' && status !== 'locked') return;
    const epoch = ++operationEpoch.current;
    // Erase first, without waiting for the remote CSRF/logout exchange.
    await eraseAuthorizedData();
    if (epoch !== operationEpoch.current) return;
    setStatus('working');
    try {
      const { auth } = clients();
      const response = await auth.logout();
      if (epoch !== operationEpoch.current) return;
      await eraseAuthorizedData();
      if (epoch !== operationEpoch.current) return;
      // A failed/unknown logout is NOT a signed-out identity; hold the
      // locked view until the server confirms logout, without stored rows.
      setStatus(response === 'signed_out' ? 'signed_out' : 'locked');
    } catch {
      if (epoch !== operationEpoch.current) return;
      await eraseAuthorizedData();
      if (epoch === operationEpoch.current) setStatus('locked');
    }
  }

  const errorMessage: Partial<Record<Status, string>> = {
    invalid_credentials: '凭据无效，未保留任何设备数据。',
    forbidden: '当前请求被拒绝，设备和监控数据已清除。',
    throttled: '登录受到频率限制；不会自动重试。',
    unavailable: '无法安全确认会话或授权数据，未保留历史记录。',
    locked: '已离开当前页面或标签；授权数据已清空，请先安全登出。',
  };

  return <main className="page" data-candidate-status={status}
    data-candidate-principal={status === 'ready' ? principal : ''}>
    <div className="title-row">
      <div>
        <p className="eyebrow">CONSOLE / NXT-06-A · DISPOSABLE ACCEPTANCE</p>
        <h1>只读管理控制台候选</h1>
        <p>仅隔离合成数据验收。普通 Next.js 登录与生产设备接口未开放。</p>
      </div>
      <span className="backend-state"><LockKeyhole size={15} aria-hidden="true" />
        仅限测试环境</span>
    </div>

    {status !== 'ready' ? <section className="login-card"
      aria-label="合成管理会话">
      <div className="login-brand"><span className="brand-mark">
        <Activity size={23} aria-hidden="true"/></span>
        <span><strong>Lanveriq</strong><small>STAGED CONSOLE</small></span></div>
      <h2>{status === 'locked' ? '会话已锁定' : '受控测试登录'}</h2>
      <p>禁止输入真实管理员凭据。</p>
      {status === 'locked' ? (
        <button className="primary-action" type="button" onClick={logout}>
          <LogOut size={16} aria-hidden="true"/>验证登出后重新登录
        </button>
      ) : <form onSubmit={onLogin} autoComplete="off" className="login-fields">
        <label htmlFor="candidate-username">合成管理员账号</label>
        <input id="candidate-username" ref={username} type="text"
          autoComplete="off" required maxLength={255}/>
        <label htmlFor="candidate-password">一次性合成密码</label>
        <input id="candidate-password" ref={password} type="password"
          autoComplete="new-password" required maxLength={4096}/>
        <button className="primary-action" type="submit"
          disabled={status === 'working'}>
          安全登录并获取授权设备 <ArrowRight size={16} aria-hidden="true"/>
        </button>
      </form>}
      <p role="status" data-testid="candidate-message">
        {status === 'working' ? '正在验证一次性合成会话…'
          : errorMessage[status] ?? '尚未建立合成管理员会话；不会自动发起任何业务请求。'}
      </p>
    </section> : <section aria-label="当前已授权设备">
      <div className="title-row">
        <div><h2>已授权运行概览</h2>
          <p>仅由服务器在当前设备组范围内计算，不包含员工活动。</p></div>
        <div className="top-actions">
          <button className="secondary-action" type="button" onClick={refresh}>
            <RefreshCcw size={16} aria-hidden="true"/>手动刷新
          </button>
          <button className="secondary-action" type="button" onClick={logout}>
            <LogOut size={16} aria-hidden="true"/>登出并清除数据
          </button>
        </div>
      </div>
      <p role="status" data-testid="candidate-principal">当前合成账号：{principal}</p>
      {overview && <div className="metric-grid" aria-label="已授权监控数据">
        {([
          ['已授权设备总数', overview.total],
          ['在线设备', overview.online],
          ['离线设备', overview.offline],
          ['待注册设备', overview.pending],
        ] as const).map(([name, value]) =>
          <Card key={name} className="metric-card">
            <CardHeader><CardDescription>{name}</CardDescription>
              <CardTitle data-testid={'candidate-metric-' + name}>{value}</CardTitle>
            </CardHeader>
          </Card>)}
      </div>}
      <DeviceDirectory state={directory} allowDetailNavigation={false}/>
      <p className="freshness-note">只显示当前授权设备，连接状态必须以服务端证据为准；
        不会根据注册状态推断在线情况。</p>
    </section>}
  </main>;
}
