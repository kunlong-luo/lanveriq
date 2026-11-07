import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

// A public static preview must not contain privately mounted Next.js auth,
// API transport, browser session, or a server-side Node runtime.
const root = resolve('out');
const required = {
  'index.html': 'Lanveriq 管理控制台预览',
  'overview/index.html': '设备运行概览',
  'monitoring/index.html': '监控中心',
  'devices/index.html': '终端资产',
  'enrollment/index.html': '设备注册',
  'settings/index.html': '系统设置',
  'login/index.html': '管理员登录',
};

if (process.env.LANVERIQ_PAGES_STATIC_EXPORT !== '1' ||
    process.env.GITHUB_ACTIONS !== 'true') {
  throw new Error('Refusing to verify a non-Pages/non-CI site as public');
}
if (!existsSync(join(root, '.nojekyll'))) {
  throw new Error('Pages output must include .nojekyll for _next assets');
}
for (const [path, marker] of Object.entries(required)) {
  const full = join(root, path);
  if (!existsSync(full)) throw new Error('Missing exported static preview route: ' + path);
  const html = readFileSync(full, 'utf8');
  if (!html.includes(marker)) throw new Error('Preview page content missing: ' + path);
  if (!html.includes('/lanveriq/_next/')) {
    throw new Error('Missing repository-relative Next.js assets: ' + path);
  }
  if (!html.includes('noindex')) {
    throw new Error('Public preview should remain unindexed: ' + path);
  }
}
const home = readFileSync(join(root, 'index.html'), 'utf8');
if (!home.includes('/lanveriq/overview/')) {
  throw new Error('Missing safe basePath navigation from Pages root');
}
const login = readFileSync(join(root, 'login/index.html'), 'utf8');
if (!login.includes('禁止输入真实账号或密码') ||
    !login.includes('disabled') ||
    /<form[\\s>]/i.test(login)) {
  throw new Error('Public Pages login must not accept real credentials');
}
if (existsSync(join(root, 'devices/00000000-0000-7000-8000-000000000001'))) {
  throw new Error('Dynamic device details must not be published');
}

const forbidden = [
  /X-Lanveriq-Synthetic-Edge-Token/i,
  /LANVERIQ_SYNTHETIC_EDGE_SECRET/i,
  /__Host-lanveriq-session/i,
  /\/api\/v1\/auth\/(?:login|csrf|session|logout)/i,
  /staged-console-auth/i,
  /synthetic-multi-admin-probe/i,
];
let assets = 0;
let scanned = 0;
function scan(dir) {
  for (const ent of readdirSync(dir, { withFileTypes: true })) {
    const pathname = join(dir, ent.name);
    if (ent.isSymbolicLink()) throw new Error('Pages must not publish symlinks');
    if (ent.isDirectory()) { scan(pathname); continue; }
    if (!ent.isFile()) throw new Error('Unexpected Pages artifact entry');
    if (/\.(?:map|pem|key|env|sqlite|jar|ts|tsx)$/.test(ent.name)) {
      throw new Error('Private source/secret-like file in Pages export');
    }
    if (ent.name.endsWith('.js')) assets++;
    const size = statSync(pathname).size;
    scanned += size;
    if (size > 16 * 1024 * 1024 || scanned > 150 * 1024 * 1024) {
      throw new Error('Unexpectedly large public Pages export');
    }
    if (/\.(?:html|js|json|txt|css)$/.test(ent.name)) {
      const body = readFileSync(pathname, 'utf8');
      for (const token of forbidden) {
        if (token.test(body)) throw new Error('Credentialed runtime marker in Pages artifact');
      }
    }
  }
}
scan(root);
if (assets === 0) throw new Error('Pages export did not include Next.js browser bundles');
console.log('PAGES PUBLIC STATIC EXPORT PASS: six disconnected routes, disabled login, fixed /lanveriq assets, no credentialed runtime.');
