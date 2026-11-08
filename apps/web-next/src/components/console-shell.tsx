'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { Dialog } from '@base-ui/react/dialog';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Activity, BarChart3, Bell, BookOpen, ChevronRight, CircleHelp,
  LayoutDashboard, Layers3, Menu, Monitor, Plus, Search, Settings2, ShieldCheck, X,
  type LucideIcon,
} from 'lucide-react';
import { activeConsoleRoute, consoleRoutes, type ConsoleRoute } from '@/lib/console-navigation';

const routeIcons: Record<ConsoleRoute['id'], LucideIcon> = {
  overview: LayoutDashboard,
  devices: Monitor,
  monitoring: Activity,
  alerts: Bell,
  enrollment: Plus,
  policies: Layers3,
  reports: BarChart3,
  settings: Settings2,
  'design-system': BookOpen,
};

function NavigationLinks({ current, onNavigate }: Readonly<{
  current: ConsoleRoute | null;
  onNavigate?: () => void;
}>) {
  return <nav className="navigation" aria-label="主导航">
    {consoleRoutes.map(({ href, label, id, planned }) => {
      const Icon = routeIcons[id];
      return <Link key={href} href={href} onClick={onNavigate}
        className={`nav-item${current?.href === href ? ' selected' : ''}`}
        aria-current={current?.href === href ? 'page' : undefined}>
        <Icon size={18} aria-hidden="true" /><span>{label}</span>
        {planned && <span className="bc-nav-planned" aria-label="规划模块">设计</span>}
        {current?.href === href &&
          <ChevronRight size={14} className="nav-chevron" aria-hidden="true"/>}
      </Link>;
    })}
  </nav>;
}

function ConsoleBrand({ onNavigate }: Readonly<{ onNavigate?: () => void }>) {
  return <Link href="/overview" className="brand" aria-label="Lanveriq 概览"
    onClick={onNavigate}>
    <span className="brand-mark" aria-hidden="true"><Layers3 size={24} /></span>
    <span><strong>Lanveriq</strong><small>ENTERPRISE CONSOLE</small></span>
  </Link>;
}

function PreviewSafetyNotice() {
  return <div className="sidebar-notice">
    <ShieldCheck size={20} aria-hidden="true" />
    <span><strong>独立安全预览</strong><small>无真实设备、管理员会话或业务数据</small></span>
  </div>;
}

/**
 * NXT-15: replace our handwritten mobile overlay/focus/Escape implementation
 * with Base UI Dialog's focus trap, dismissal and focus restoration.
 * Desktop sidebar remains ordinary semantic navigation, not a dialog.
 * The entire preview is disconnected from all business APIs.
 */
export function ConsoleShell({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const current = activeConsoleRoute(pathname);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  return <>
    <a href="#main" className="skip-to-content">跳转到主要内容</a>
    <div className="shell bc-console">
      <aside id="console-sidebar" className="sidebar">
        <div className="bc-brand-row"><ConsoleBrand /></div>
        <p className="nav-label">WORKSPACE / 工作空间</p>
        <NavigationLinks current={current} />
        <PreviewSafetyNotice />
        <p className="bc-sidebar-signoff">LANVERIQ / PRIVATE BY DESIGN</p>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="bc-header-left">
            <Dialog.Root open={mobileOpen} onOpenChange={setMobileOpen}>
              <Dialog.Trigger type="button" className="bc-menu-button"
                aria-label="打开导航" aria-controls="console-mobile-navigation">
                <Menu size={21} aria-hidden="true"/>
              </Dialog.Trigger>
              <Dialog.Portal>
                <Dialog.Backdrop className="bc-base-dialog-backdrop" />
                <Dialog.Popup id="console-mobile-navigation" className="bc-mobile-sheet">
                  <div className="bc-brand-row">
                    <ConsoleBrand onNavigate={() => setMobileOpen(false)} />
                    <Dialog.Close type="button" className="bc-close-nav" aria-label="关闭导航">
                      <X size={20} aria-hidden="true" />
                    </Dialog.Close>
                  </div>
                  <Dialog.Title className="sr-only">Lanveriq 移动端主导航</Dialog.Title>
                  <Dialog.Description className="sr-only">
                    选择一个工作空间页面；关闭后焦点返回菜单按钮。
                  </Dialog.Description>
                  <p className="nav-label">WORKSPACE / 工作空间</p>
                  <NavigationLinks current={current} onNavigate={() => setMobileOpen(false)} />
                  <PreviewSafetyNotice />
                  <p className="bc-sidebar-signoff">LANVERIQ / PRIVATE BY DESIGN</p>
                </Dialog.Popup>
              </Dialog.Portal>
            </Dialog.Root>
            <div className="breadcrumb"><span>工作空间</span>
              <ChevronRight size={15} aria-hidden="true" />
              <strong>{current?.label ?? '管理控制台'}</strong>
            </div>
          </div>
          <div className="top-actions">
            <button type="button" disabled className="bc-global-search"
              aria-label="全局搜索尚未开放">
              <Search size={17} aria-hidden="true" />
              <span>搜索设备、资产或操作</span><kbd>⌘ K</kbd>
            </button>
            <span className="backend-state"><span aria-hidden="true" />业务 API 未连接</span>
            <button type="button" disabled aria-label="通知尚未接入" className="icon-button">
              <Bell size={19}/></button>
            <Link href="/design-system" className="icon-button bc-help-link"
              aria-label="查看设计系统与交互规范"><CircleHelp size={19}/></Link>
            <Link href="/login" className="demo-avatar"
              aria-label="查看尚未开放的管理员登录">DEMO</Link>
          </div>
        </header>
        <main id="main" tabIndex={-1} className="main-content">
          <div className="warning-banner" role="status">
            <span className="bc-preview-dot" aria-hidden="true"/>
            <strong>独立预览 · 不含真实数据</strong>
            <span>Next.js App Router 设计预览；未连接 PostgreSQL、Redis、业务 API 或真实终端。</span>
            <span className="bc-preview-label">PREVIEW ONLY</span>
          </div>
          {children}
        </main>
        <footer className="footer">
          Lanveriq · B+C 设计预览 · 尚未开放真实管理员登录、设备监控或操作
        </footer>
      </div>
    </div>
  </>;
}
