#!/usr/bin/env bash
# Explicit single-owner local MVP-06/07 acceptance, not a deployable route.
# No synthetic CI gate override, repository edits, DB writes or Caddy changes.
set -euo pipefail
umask 077
if [[ "${1:-}" != "--accept-local-owner-test" || "$#" -gt 2 ||
      ( "$#" == 2 && "${2:-}" != "--allow-online-install" ) ||
      "${GITHUB_ACTIONS:-}" == true || -n "${CI:-}" || "$EUID" == 0 ]]; then
    echo "REFUSED: explicit non-root local-owner acceptance only" >&2
    exit 1
fi
: "${XDG_RUNTIME_DIR:?A private Linux user runtime directory is required}"
[[ "$XDG_RUNTIME_DIR" = /* && -d "$XDG_RUNTIME_DIR" &&
   -O "$XDG_RUNTIME_DIR" && ! -L "$XDG_RUNTIME_DIR" ]] || exit 1
root="$(git rev-parse --show-toplevel)"
[[ "$(git -C "$root" branch --show-current)" == main ]] || exit 1
# Pin the committed source once; the archive deliberately excludes every
# local edit, staged change, generated next-env.d.ts and untracked file.
# Do not require a clean user worktree, but do require an unmodified launcher.
source_sha="$(git -C "$root" rev-parse --verify HEAD)"
launcher="$root/apps/web/scripts/run-local-console-probe.sh"
git -C "$root" show "$source_sha:apps/web/scripts/run-local-console-probe.sh" |
    cmp -s - "$launcher" || {
    echo "REFUSED: launcher differs from committed source" >&2; exit 1;
}
if ! git -C "$root" diff --quiet "$source_sha" -- apps/web; then
    echo "NOTICE: local web modifications preserved; committed archive only"
fi
echo "Acceptance source: $source_sha"
[[ -z "$(ss -H -ltn '( sport = :3100 )')" ]] || {
    echo "REFUSED: port 3100 already listening" >&2; exit 1;
}
parent="$XDG_RUNTIME_DIR/lanveriq"
mkdir -p "$parent"
[[ -O "$parent" && ! -L "$parent" ]] || exit 1
work="$parent/console-probe"
mkdir -m 700 "$work" || {
    echo "REFUSED: prior probe directory exists; inspect it first" >&2; exit 1;
}
trap 'rm -rf -- "$work"' EXIT
git -C "$root" archive "$source_sha" apps/web | tar -x -C "$work"
cd "$work/apps/web"
unset LANVERIQ_PAGES_STATIC_EXPORT
export NEXT_TELEMETRY_DISABLED=1
if [[ "${2:-}" == "--allow-online-install" ]]; then
    echo "Dependency installation: explicit online permission; frozen lockfile"
    pnpm install --frozen-lockfile
else
    echo "Dependency installation: offline; frozen lockfile"
    pnpm install --offline --frozen-lockfile || {
        echo "Offline install failed; rerun with --allow-online-install if approved" >&2
        exit 1
    }
fi
pnpm run verify:routes
mkdir -m 700 src/app/local-console-probe
cat > src/local-console-probe-edge.ts <<'EOF'
import { timingSafeEqual } from 'node:crypto';

export function acceptsLocalProbeEdge(supplied: string | null, expected: string | undefined) {
  if (!supplied || !expected || !/^[a-f0-9]{64}$/.test(expected) ||
      !/^[a-f0-9]{64}$/.test(supplied)) return false;
  return timingSafeEqual(Buffer.from(supplied), Buffer.from(expected));
}
EOF
cat > src/proxy.ts <<'EOF'
import { NextResponse, type NextRequest } from 'next/server';
import { acceptsLocalProbeEdge } from './local-console-probe-edge';

export const config = { matcher: ['/local-console-probe/:path*'] };
export function proxy(request: NextRequest) {
  if (!acceptsLocalProbeEdge(request.headers.get('x-lanveriq-local-probe-edge'),
      process.env.LANVERIQ_LOCAL_PROBE_EDGE_SECRET)) {
    return new NextResponse('Not found', { status: 404, headers: {
      'Cache-Control': 'no-store', 'Content-Type': 'text/plain; charset=utf-8',
      'X-Content-Type-Options': 'nosniff',
    } });
  }
  const response = NextResponse.next();
  response.headers.set('Cache-Control', 'no-store');
  return response;
}
EOF
cat > src/app/local-console-probe/page.tsx <<'EOF'
import { headers } from 'next/headers';
import { notFound } from 'next/navigation';
import { acceptsLocalProbeEdge } from '@/local-console-probe-edge';
import { LocalConsoleProbe } from '@/local-console-probe';

export const dynamic = 'force-dynamic';
export default async function Page() {
  const incoming = await headers();
  if (!acceptsLocalProbeEdge(incoming.get('x-lanveriq-local-probe-edge'),
      process.env.LANVERIQ_LOCAL_PROBE_EDGE_SECRET)) notFound();
  return <LocalConsoleProbe />;
}
EOF
cat > src/local-console-probe.tsx <<'EOF'
'use client';
import { useEffect, useRef, useState } from 'react';
import { createStagedNextConsoleAuth } from './lib/staged-console-auth';
import { createStagedConsoleReader, type BrowserFetch } from './lib/staged-console-read';
import { projectLocalInventory } from './lib/console-device-projection';
import { type DirectoryRow } from './components/device-directory';
import { isCanonicalUuid } from './lib/uuid';

const ORIGIN = 'https://console.lanveriq.internal';
type Phase = 'idle' | 'working' | 'verified' | 'denied' | 'unavailable' | 'locked' | 'signed-out';
export function LocalConsoleProbe() {
  const username = useRef<HTMLInputElement>(null);
  const password = useRef<HTMLInputElement>(null);
  const device = useRef<HTMLInputElement>(null);
  const identity = useRef<{login: string; deviceId: string} | null>(null);
  const generation = useRef(0);
  const busy = useRef(false);
  const [allowed, setAllowed] = useState(false);
  const [phase, setPhase] = useState<Phase>('idle');
  const [inventory, setInventory] = useState<DirectoryRow | null>(null);

  function clearFields() {
    for (const field of [username, password, device]) {
      if (field.current) field.current.value = '';
    }
  }
  useEffect(() => {
    setAllowed(window.location.origin === ORIGIN);
    const clear = (next: 'locked' | 'unavailable') => {
      generation.current++;
      identity.current = null;
      clearFields();
      setInventory(null);
      setPhase(next);
    };
    const lock = () => clear('locked');
    const offline = () => clear('unavailable');
    const visibility = () => { if (document.hidden) lock(); };
    document.addEventListener('visibilitychange', visibility);
    window.addEventListener('pagehide', lock);
    window.addEventListener('offline', offline);
    return () => {
      document.removeEventListener('visibilitychange', visibility);
      window.removeEventListener('pagehide', lock);
      window.removeEventListener('offline', offline);
      generation.current++;
      identity.current = null;
      clearFields();
    };
  }, []);
  function clients() {
    const actual = window.location.origin;
    const fetcher: BrowserFetch = (input, init) => window.fetch(input, init);
    return {
      auth: createStagedNextConsoleAuth(ORIGIN, actual, fetcher, async () => setInventory(null)),
      reader: createStagedConsoleReader(ORIGIN, actual, fetcher),
    };
  }
  async function readInventory(expected: string, current: number) {
    const { reader } = clients();
    const page = await reader.list();
    if (generation.current !== current) return;
    if (page.kind !== 'ready') {
      setPhase(page.kind === 'unavailable' ? 'unavailable' : 'denied');
      return;
    }
    if (page.hasMore || page.rows.length !== 1 || page.rows[0].id !== expected) {
      setPhase('denied'); return;
    }
    const detail = await reader.detail(expected);
    if (generation.current !== current) return;
    const projected = projectLocalInventory(page, detail, expected);
    if (!projected) {
      setPhase(detail.kind === 'unavailable' || detail.kind === 'ok' ? 'unavailable' : 'denied');
      return;
    }
    setInventory(projected);
    setPhase('verified');
  }
  async function login() {
    if (!allowed || busy.current || (phase !== 'idle' && phase !== 'signed-out')) return;
    const user = username.current?.value ?? '';
    const secret = password.current?.value ?? '';
    const expected = device.current?.value.trim().toLowerCase() ?? '';
    clearFields();
    setInventory(null);
    identity.current = null;
    if (!user || !secret || !isCanonicalUuid(expected)) {
      setPhase('denied'); return;
    }
    busy.current = true;
    const current = ++generation.current;
    setPhase('working');
    try {
      const { auth } = clients();
      const session = await auth.login(user, secret);
      if (generation.current !== current) return;
      if (session.kind !== 'authenticated' || session.login !== user) {
        setPhase(session.kind === 'unavailable' ? 'unavailable' : 'denied'); return;
      }
      identity.current = {login:user, deviceId:expected};
      await readInventory(expected, current);
    } catch {
      if (generation.current === current) setPhase('unavailable');
    } finally { busy.current = false; }
  }
  async function refresh() {
    const expected = identity.current;
    if (!allowed || busy.current || phase !== 'verified' || !expected) return;
    busy.current = true;
    const current = ++generation.current;
    setInventory(null);
    setPhase('working');
    try {
      const session = await clients().auth.session();
      if (generation.current !== current) return;
      if (session.kind !== 'authenticated' || session.login !== expected.login) {
        setPhase(session.kind === 'unavailable' ? 'unavailable' : 'denied'); return;
      }
      await readInventory(expected.deviceId, current);
    } catch {
      if (generation.current === current) setPhase('unavailable');
    } finally { busy.current = false; }
  }
  async function logout() {
    if (!allowed || busy.current) return;
    busy.current = true;
    const current = ++generation.current;
    clearFields();
    identity.current = null;
    setInventory(null);
    setPhase('working');
    try {
      const { auth, reader } = clients();
      const result = await auth.logout();
      const session = await auth.session();
      const page = await reader.list();
      if (generation.current === current) {
        setPhase(result === 'signed_out' && session.kind === 'signed_out' &&
          page.kind === 'signed_out' ? 'signed-out' :
          result === 'unavailable' || session.kind === 'unavailable' ||
          page.kind === 'unavailable' ? 'unavailable' : 'denied');
      }
    } catch {
      if (generation.current === current) setPhase('unavailable');
    } finally { busy.current = false; }
  }
  const canLogin = allowed && (phase === 'idle' || phase === 'signed-out');
  // No form, field names, server action, browser storage or automatic requests.
  return <main style={{maxWidth: 960, margin: '4rem auto', padding: '1rem'}}>
    <h1>Lanveriq 本机设备库存验收</h1>
    <p>仅设备所有人临时只读测试；仅展示当前授权的真实 API 库存。</p>
    <label htmlFor="probe-user">测试管理员</label>
    <input id="probe-user" ref={username} disabled={!canLogin} maxLength={255}
      autoComplete="off" type="text" />
    <label htmlFor="probe-password">密码</label>
    <input id="probe-password" ref={password} disabled={!canLogin} maxLength={4096}
      autoComplete="new-password" type="password" />
    <label htmlFor="probe-device">已入库设备 UUID</label>
    <input id="probe-device" ref={device} disabled={!canLogin} maxLength={36}
      autoComplete="off" type="text" />
    <button type="button" disabled={!canLogin} onClick={login}>登录并读取设备</button>
    <button type="button" disabled={!allowed || phase !== 'verified'} onClick={refresh}>
      手动刷新
    </button>
    <button type="button" disabled={!allowed || phase === 'working'} onClick={logout}>
      登出并验证拒绝
    </button>
    <p role="status" id="local-probe-state" data-state={phase}>
      {!allowed ? '拒绝：仅允许规范 HTTPS Console 来源' :
        phase === 'verified' ? 'PASS：真实会话、唯一设备列表及同一设备详情的 OS/版本/CPU/内存一致' :
        phase === 'signed-out' ? 'PASS：登出后会话及设备读取均被拒绝' :
        phase === 'locked' ? '页面已锁定，库存已清除，请先登出' :
        phase === 'denied' ? '拒绝：会话、权限或设备身份未通过，库存已清除' :
        phase === 'unavailable' ? '设备服务暂不可用或库存不一致，库存已清除；请恢复服务后登出再试' :
        phase === 'working' ? '正在重新验证，旧库存已清除' : '待操作'}
    </p>
    {allowed && phase === 'verified' && inventory && <>
      <section aria-label="设备列表" data-testid="local-inventory-list">
        <h2>设备列表（唯一授权设备）</h2>
        <table><thead><tr><th scope="col">设备</th><th scope="col">操作系统</th>
          <th scope="col">CPU</th><th scope="col">内存（字节）</th></tr></thead>
          <tbody><tr><th scope="row">{inventory.label}</th><td>{inventory.os} {inventory.osVersion}</td>
            <td>{inventory.cpu}</td><td>{inventory.memoryBytes}</td></tr></tbody></table>
      </section>
      <section aria-label="同一设备详情" data-testid="local-inventory-detail">
        <h2>同一设备详情</h2>
        <dl><dt>操作系统</dt><dd>{inventory.os}</dd>
          <dt>系统版本</dt><dd>{inventory.osVersion}</dd>
          <dt>CPU 型号</dt><dd>{inventory.cpu}</dd>
          <dt>内存</dt><dd>{inventory.memoryBytes} 字节</dd>
          <dt>连接状态</dt><dd>待确认（不推断在线）</dd></dl>
      </section>
    </>}
  </main>;
}
EOF

# Native Node assertions check the actual generated proof helper before serving.
node --input-type=module <<'EOF'
import assert from 'node:assert/strict';
import { acceptsLocalProbeEdge as accepts } from './src/local-console-probe-edge.ts';
const valid = 'a'.repeat(64);
for (const supplied of [null, '', 'b'.repeat(64), 'a'.repeat(63), 'G'.repeat(64),
                        valid + ',' + valid]) assert.equal(accepts(supplied, valid), false);
assert.equal(accepts(valid, undefined), false);
assert.equal(accepts(valid, 'bad'), false);
assert.equal(accepts(valid, valid), true);
console.log('PASS: local probe edge proof positive and negative assertions');
EOF
pnpm typecheck
pnpm build

# Secret exists only for this run and is never printed or bundled into JS.
export LANVERIQ_LOCAL_PROBE_EDGE_SECRET
LANVERIQ_LOCAL_PROBE_EDGE_SECRET="$(openssl rand -hex 32)"
printf 'header_up X-Lanveriq-Local-Probe-Edge %s\n' \
    "$LANVERIQ_LOCAL_PROBE_EDGE_SECRET" > "$work/edge.caddy"
echo "Private Caddy snippet ready: $work/edge.caddy (do not print its contents)"
echo "After installing the snippet and reloading Caddy, open the canonical HTTPS probe."
echo "Stop with Ctrl-C; remove the temporary Caddy route and snippet separately."
pnpm exec next start --hostname 127.0.0.1 --port 3100
