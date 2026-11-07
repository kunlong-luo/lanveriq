/**
 * The canonical Next.js App Router navigation contract.
 *
 * Presentational only: no React Router, legacy Vite routing, API calls,
 * authorization claims, credentials or synthetic data fixtures.
 */
export const consoleRoutes = [
  { href: '/overview', label: '概览', id: 'overview' },
  { href: '/monitoring', label: '监控中心', id: 'monitoring' },
  { href: '/devices', label: '终端资产', id: 'devices' },
  { href: '/enrollment', label: '设备注册', id: 'enrollment' },
  { href: '/settings', label: '系统设置', id: 'settings' },
] as const;

export type ConsoleRoute = (typeof consoleRoutes)[number];

export function activeConsoleRoute(pathname: string | null): ConsoleRoute | null {
  if (!pathname?.startsWith('/')) return null;
  return consoleRoutes.find(route =>
    pathname === route.href || pathname.startsWith(route.href + '/')
  ) ?? null;
}
