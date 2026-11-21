import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { SafetyGate, UnconnectedDeviceState } from './preview-state';
import { LoginPreview } from './login-preview';

describe('Next.js migration candidate: synthetic-only rendering', () => {
  it('does not misreport an unconnected database as a zero-device database', () => {
    const html = renderToStaticMarkup(<UnconnectedDeviceState />);
    expect(html).toContain('设备数据尚未连接');
    expect(html).toContain('不代表数据库中没有设备');
    expect(html).not.toContain('演示设备 A');
    expect(html).not.toContain('0 台设备');
  });
  it('shows the exact operational gates, with no enabled action buttons', () => {
    const html = renderToStaticMarkup(<SafetyGate />);
    expect(html).toContain('PostgreSQL 18');
    expect(html).toContain('Caddy HTTPS');
    expect(html).toContain('Spring Security');
    expect(html).not.toContain('form action=');
  });
  it('disables all credentials and never posts a login form', () => {
    const html = renderToStaticMarkup(<LoginPreview />);
    expect(html).toContain('管理员登录');
    expect(html).toContain('禁止输入真实账号或密码');
    expect(html).toContain('type="password"');
    expect(html).toContain('disabled');
    expect(html).not.toContain('action="/api');
    expect(html).not.toContain('method="post"');
  });
});
