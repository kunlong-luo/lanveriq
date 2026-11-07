import { describe, expect, it } from 'vitest';
import { activeConsoleRoute, consoleRoutes } from './console-navigation';

describe('canonical Next.js App Router navigation', () => {
  it('has exactly the non-credentialed main console preview routes', () => {
    expect(consoleRoutes.map(route => route.href)).toEqual([
      '/overview', '/monitoring', '/devices', '/enrollment', '/settings',
    ]);
    expect(new Set(consoleRoutes.map(route => route.href)).size)
      .toBe(consoleRoutes.length);
    expect(consoleRoutes.every(route => !route.href.includes('/api/'))).toBe(true);
  });

  it('resolves nested device paths without confusing other route prefixes', () => {
    expect(activeConsoleRoute('/devices')?.id).toBe('devices');
    expect(activeConsoleRoute('/devices/00000000-0000-7000-8000-000000000001')?.id)
      .toBe('devices');
    expect(activeConsoleRoute('/overview')?.label).toBe('概览');
    expect(activeConsoleRoute('/monitoring')?.label).toBe('监控中心');
    expect(activeConsoleRoute('/devices-unsafe')).toBeNull();
    expect(activeConsoleRoute('/api/v1/devices')).toBeNull();
    expect(activeConsoleRoute('/login')).toBeNull();
    expect(activeConsoleRoute(null)).toBeNull();
  });
});
