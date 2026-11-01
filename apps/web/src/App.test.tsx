import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import App from './App';

function renderRoute(path: string) {
  return renderToStaticMarkup(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('Web synthetic-only navigation', () => {
  it('shows demo-mode notice on every route', () => {
    for (const route of ['/overview', '/monitoring', '/devices',
      '/devices/00000000-0000-7000-8000-000000000001',
      '/enrollment', '/settings']) {
      const page = renderRoute(route);
      expect(page).toContain('演示模式');
      expect(page).toContain('未连接后端');
      expect(page).toContain('全部为合成数据');
    }
  });

  it('renders synthetic monitoring without any real screen thumbnails', () => {
    const page = renderRoute('/monitoring');
    expect(page).toContain('终端健康明细');
    expect(page).toContain('不是员工屏幕画面');
    expect(page).toContain('演示设备 A');
  });

  it('renders disabled login fields and no credential submission action', () => {
    const page = renderRoute('/login');
    expect(page).toContain('管理员登录');
    expect(page).toContain('演示模式');
    expect(page).toContain('全部为合成数据');
    expect(page).toContain('未连接后端');
    expect(page).toContain('请勿输入真实账号或密码');
    expect(page).toContain('安全登录（尚未开放）');
    expect(page).toContain('type="password"');
    expect(page).toContain('disabled');
    expect(page).not.toContain('action="/api/v1/auth/login"');
  });

  it('does not expose operational enrollment controls', () => {
    const page = renderRoute('/enrollment');
    expect(page).toContain('发起注册（未启用）');
    expect(page).toContain('disabled');
  });

  it('does not leak unknown device details', () => {
    const page = renderRoute('/devices/00000000-0000-7000-8000-999999999999');
    expect(page).toContain('演示设备不存在');
    expect(page).not.toContain('Intel Core i7');
  });

  it('has a distinct synthetic overview and read-only inventory detail', () => {
    expect(renderRoute('/overview')).toContain('设备连接分布');
    const detail = renderRoute('/devices/00000000-0000-7000-8000-000000000001');
    expect(detail).toContain('设备操作（暂不可用）');
    expect(detail).toContain('撤销证书');
    expect(detail).toContain('disabled');
  });
});
