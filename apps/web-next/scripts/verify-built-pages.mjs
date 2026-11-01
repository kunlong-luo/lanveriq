import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const base = 'http://127.0.0.1:3100';
const server = spawn('pnpm', ['exec', 'next', 'start', '--hostname', '127.0.0.1', '--port', '3100'], {
  cwd: process.cwd(), stdio: ['ignore', 'pipe', 'pipe'], detached: true,
  env: { ...process.env, NODE_ENV: 'production' },
});
let output = '';
for (const stream of [server.stdout, server.stderr]) {
  stream.setEncoding('utf8');
  stream.on('data', part => { output += part; });
}
try {
  let ready = false;
  for (let attempt = 0; attempt < 70; attempt++) {
    if (server.exitCode !== null) throw new Error('Next exited early: ' + output);
    try {
      const response = await fetch(base + '/overview', {
        redirect: 'manual', signal: AbortSignal.timeout(2000),
      });
      if (response.status === 200) { ready = true; break; }
    } catch { /* Next is still starting */ }
    await sleep(400);
  }
  if (!ready) throw new Error('Next preview did not start: ' + output);

  const pages = {
    '/overview': '设备运行概览',
    '/monitoring': '监控中心',
    '/devices': '终端资产',
    '/devices/00000000-0000-7000-8000-000000000001': '设备详情（未连接）',
    '/enrollment': '设备注册',
    '/settings': '系统设置',
    '/login': '管理员登录',
  };
  for (const [route, label] of Object.entries(pages)) {
    const response = await fetch(base + route, {
      redirect: 'manual', signal: AbortSignal.timeout(6000),
    });
    if (response.status !== 200) throw new Error(route + ': expected 200; got ' + response.status);
    const html = await response.text();
    if (!html.includes(label)) throw new Error(route + ': missing ' + label);
    if (route === '/login') {
      if (!html.includes('disabled') || !html.includes('禁止输入真实账号或密码')) {
        throw new Error('Login credentials must be disabled and unsubmitted');
      }
    } else if (!html.includes('不含真实数据')) {
      throw new Error(route + ': missing synthetic-only preview warning');
    }
    if (/set-cookie/i.test([...response.headers.keys()].join(' '))) {
      throw new Error(route + ': synthetic preview unexpectedly issued cookies');
    }
  }

  for (const route of ['/api/v1/devices', '/actuator/health', '/devices/not-a-uuid']) {
    const response = await fetch(base + route, {
      redirect: 'manual', signal: AbortSignal.timeout(6000),
    });
    if (response.status !== 404) {
      throw new Error(route + ': Next.js must not proxy or accept this path; got ' + response.status);
    }
  }
  const home = await fetch(base + '/', {
    redirect: 'manual', signal: AbortSignal.timeout(6000),
  });
  if (!([307, 308].includes(home.status)) ||
      new URL(home.headers.get('location') ?? '', base).pathname !== '/overview') {
    throw new Error('Unexpected root redirect');
  }
  console.log('NXT-01 actual Next.js production server: seven pages, disabled login, no API proxy, root redirect passed');
} finally {
  if (server.pid) {
    try { process.kill(-server.pid, 'SIGTERM'); } catch { /* already stopped */ }
  }
}
