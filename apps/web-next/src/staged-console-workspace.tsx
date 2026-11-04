'use client';

import { useRef, useState, type FormEvent } from 'react';
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
 * No effect hooks, session restoration, interval polling, external proxy,
 * localStorage, retry, Agent request or employee activity collection.
 * Every request originates from a deliberate user-like button/form action.
 */
const ORIGIN = 'https://console.lanveriq.internal';

type Status = 'signed_out' | 'working' | 'ready' | 'invalid_credentials' |
  'forbidden' | 'throttled' | 'unavailable';

const initialDirectory: DirectoryState = { kind: 'unconnected' };

export function StagedConsoleWorkspace() {
  const username = useRef<HTMLInputElement>(null);
  const password = useRef<HTMLInputElement>(null);
  const [client] = useState(createConsoleQueryClient);
  const [status, setStatus] = useState<Status>('signed_out');
  const [principal, setPrincipal] = useState('');
  const [directory, setDirectory] = useState<DirectoryState>(initialDirectory);
  const [overview, setOverview] = useState<ScopedConsoleOverview | null>(null);

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

  async function fetchAuthorized(login: string): Promise<void> {
    const { reader } = clients();
    const page = await reader.list();
    if (page.kind !== 'ready') {
      await eraseAuthorizedData();
      setStatus(page.kind === 'loading' || page.kind === 'unconnected'
        ? 'unavailable' : page.kind === 'signed_out'
        ? 'signed_out' : page.kind);
      return;
    }
    const result = await reader.overview();
    const parsed = result.kind === 'ok'
      ? parseScopedConsoleOverview(result.data) : null;
    if (!parsed) {
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
    if (status === 'working') return;
    const loginName = username.current?.value ?? '';
    const secret = password.current?.value ?? '';
    // No credentials in React state, query cache, URL, diagnostics or logs.
    if (username.current) username.current.value = '';
    if (password.current) password.current.value = '';
    await eraseAuthorizedData();
    setStatus('working');
    try {
      const { auth } = clients();
      const response = await auth.login(loginName, secret);
      if (response.kind !== 'authenticated' || response.login !== loginName) {
        await eraseAuthorizedData();
        setStatus(response.kind === 'invalid_credentials' ? 'invalid_credentials'
          : response.kind === 'forbidden' ? 'forbidden'
          : response.kind === 'throttled' ? 'throttled' : 'unavailable');
        return;
      }
      await fetchAuthorized(loginName);
    } catch {
      await eraseAuthorizedData();
      setStatus('unavailable');
    }
  }

  async function refresh() {
    if (status !== 'ready') return;
    const login = principal;
    await eraseAuthorizedData();
    setStatus('working');
    try {
      await fetchAuthorized(login);
    } catch {
      await eraseAuthorizedData();
      setStatus('unavailable');
    }
  }

  async function logout() {
    if (status !== 'ready') return;
    setStatus('working');
    try {
      const { auth } = clients();
      const response = await auth.logout();
      await eraseAuthorizedData();
      // An unavailable logout cannot be presented as a verified sign-out.
      // Local authorized data has already been erased either way.
      setStatus(response === 'signed_out' ? 'signed_out' : 'unavailable');
    } catch {
      await eraseAuthorizedData();
      setStatus('unavailable');
    }
  }

  const errorMessage: Partial<Record<Status, string>> = {
    invalid_credentials: '凭据无效，未保留任何设备数据。',
    forbidden: '当前请求被拒绝，设备和监控数据已清除。',
    throttled: '登录受到频率限制；不会自动重试。',
    unavailable: '无法安全确认会话或授权数据，未保留历史记录。',
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
      <h2>受控测试登录</h2>
      <p>禁止输入真实管理员凭据。</p>
      <form onSubmit={onLogin} autoComplete="off" className="login-fields">
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
      </form>
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
