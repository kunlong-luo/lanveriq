'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Activity, Bell, ChevronRight, LayoutDashboard, Monitor, Plus, Settings2, ShieldCheck, type LucideIcon } from 'lucide-react';
import { activeConsoleRoute, consoleRoutes, type ConsoleRoute } from '@/lib/console-navigation';

const routeIcons: Record<ConsoleRoute['id'], LucideIcon> = {
  overview: LayoutDashboard,
  monitoring: Activity,
  devices: Monitor,
  enrollment: Plus,
  settings: Settings2,
};

export function ConsoleShell({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const current = activeConsoleRoute(pathname);
  return <><a href="#main" className="skip-to-content">跳转到主要内容</a><div className="shell">
    <aside className="sidebar">
      <Link href="/overview" className="brand" aria-label="Lanveriq 概览">
        <span className="brand-mark"><Activity size={23} aria-hidden="true" /></span>
        <span><strong>Lanveriq</strong><small>MANAGEMENT CONSOLE</small></span>
      </Link>
      <p className="nav-label">工作空间</p>
      <nav className="navigation" aria-label="主导航">
        {consoleRoutes.map(({ href, label, id }) => {
          const Icon = routeIcons[id];
          return <Link key={href} href={href}
            className={`nav-item${current?.href === href ? ' selected' : ''}`}
            aria-current={current?.href === href ? 'page' : undefined}>
            <Icon size={19} aria-hidden="true" /><span>{label}</span>
            <ChevronRight size={15} className="nav-chevron" aria-hidden="true" />
          </Link>;
        })}
      </nav>
      <div className="sidebar-notice"><ShieldCheck size={20} aria-hidden="true" />
        <span><strong>安全默认关闭</strong><small>无真实设备采集</small></span>
      </div>
    </aside>
    <div className="workspace">
      <header className="topbar">
        <div className="breadcrumb"><span>工作空间</span><ChevronRight size={15} aria-hidden="true" />
          <strong>{current?.label ?? '管理控制台'}</strong></div>
        <div className="top-actions">
          <span className="backend-state"><span aria-hidden="true" />业务 API 未连接</span>
          <button type="button" disabled aria-label="通知尚未接入" className="icon-button"><Bell size={19}/></button>
          <Link href="/login" className="demo-avatar" aria-label="查看尚未开放的管理员登录">DEMO</Link>
        </div>
      </header>
      <main id="main" className="main-content">
        <div className="warning-banner" role="status"><span aria-hidden="true" />
          <strong>独立预览 · 不含真实数据</strong>
          <span>Next.js App Router 主前端预览；未连接 PostgreSQL、Redis、业务 API 或真实终端。</span>
        </div>
        {children}
      </main>
      <footer className="footer">Lanveriq V0.1 · Next.js App Router · 不可用于真实终端监控</footer>
    </div>
  </div></>;
}
