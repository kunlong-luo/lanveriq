/**
 * Canonical navigation for the disconnected Next.js App Router UI.
 * Routes marked "planned" represent design-only surfaces, NOT live services,
 * device access, authenticated scopes or approved production capabilities.
 */
export const consoleRoutes = [
  { href: '/overview', label: '概览', id: 'overview', planned: false },
  { href: '/devices', label: '终端资产', id: 'devices', planned: false },
  { href: '/monitoring', label: '监控中心', id: 'monitoring', planned: false },
  { href: '/alerts', label: '告警中心', id: 'alerts', planned: true },
  { href: '/enrollment', label: '设备注册', id: 'enrollment', planned: false },
  { href: '/policies', label: '策略管理', id: 'policies', planned: true },
  { href: '/reports', label: '报表分析', id: 'reports', planned: true },
  { href: '/settings', label: '系统设置', id: 'settings', planned: false },
  { href: '/design-system', label: '设计系统', id: 'design-system', planned: true },
] as const;

export type ConsoleRoute = (typeof consoleRoutes)[number];

export function activeConsoleRoute(pathname: string | null): ConsoleRoute | null {
  if (!pathname?.startsWith('/')) return null;
  return consoleRoutes.find(route =>
    pathname === route.href || pathname.startsWith(route.href + '/')
  ) ?? null;
}
