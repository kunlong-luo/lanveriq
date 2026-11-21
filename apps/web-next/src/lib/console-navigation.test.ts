import { describe, expect, it } from 'vitest';
import { activeConsoleRoute, consoleRoutes, findConsolePages } from './console-navigation';

describe('canonical Next.js App Router navigation', () => {
  it('lists only public disconnected UI routes, and labels planned modules', () => {
    expect(consoleRoutes.map(route => route.href)).toEqual([
      '/overview', '/devices', '/monitoring', '/alerts', '/enrollment',
      '/policies', '/reports', '/settings', '/design-system',
    ]);
    expect(new Set(consoleRoutes.map(route => route.href)).size)
      .toBe(consoleRoutes.length);
    expect(consoleRoutes.every(route => !route.href.includes('/api/'))).toBe(true);
    expect(consoleRoutes.filter(route => route.planned).map(route => route.id)).toEqual(
      ['alerts', 'policies', 'reports', 'design-system']);
  });

  it('resolves nested device paths without confusing other route prefixes', () => {
    expect(activeConsoleRoute('/devices')?.id).toBe('devices');
    expect(activeConsoleRoute('/devices/00000000-0000-7000-8000-000000000001')?.id)
      .toBe('devices');
    expect(activeConsoleRoute('/overview')?.label).toBe('概览');
    expect(activeConsoleRoute('/monitoring')?.label).toBe('监控中心');
    expect(activeConsoleRoute('/alerts')?.planned).toBe(true);
    expect(activeConsoleRoute('/design-system')?.id).toBe('design-system');
    expect(activeConsoleRoute('/devices-unsafe')).toBeNull();
    expect(activeConsoleRoute('/api/v1/devices')).toBeNull();
    expect(activeConsoleRoute('/login')).toBeNull();
    expect(activeConsoleRoute(null)).toBeNull();
  });
  it('filters only canonical disconnected pages, including mixed-width input', () => {
    expect(findConsolePages('')).toEqual(consoleRoutes);
    expect(findConsolePages(' 设备 ' ).map(route => route.href)).toEqual(['/enrollment']);
    expect(findConsolePages('MONITORING').map(route => route.href)).toEqual(['/monitoring']);
    expect(findConsolePages('ｒｅｐｏｒｔｓ').map(route => route.href)).toEqual(['/reports']);
    expect(findConsolePages('/api/v1/devices')).toEqual([]);
    expect(findConsolePages('../../../login')).toEqual([]);
    expect(findConsolePages('远程控制')).toEqual([]);
    expect(findConsolePages(' ').every(route => !route.href.startsWith('/api/'))).toBe(true);
  });
});
