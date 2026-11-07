'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
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

/**
 * B+C prototype-aligned shell. Existing App Router routes remain genuine
 * links, and planned routes render disconnected design-only pages. Nothing
 * here sends requests, saves credentials or makes any backend/service claim.
 */
export function ConsoleShell({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const current = activeConsoleRoute(pathname);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [mobileViewport, setMobileViewport] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const media = window.matchMedia('(max-width: 750px)');
    const synchronize = () => setMobileViewport(media.matches);
    synchronize();
    media.addEventListener('change', synchronize);
    return () => media.removeEventListener('change', synchronize);
  }, []);

  useEffect(() => {
    if (mobileViewport && mobileOpen) closeButtonRef.current?.focus();
  }, [mobileViewport, mobileOpen]);

  function closeMobileAndRestoreFocus() {
    setMobileOpen(false);
    menuButtonRef.current?.focus();
  }

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!mobileOpen) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') closeMobileAndRestoreFocus();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [mobileOpen]);

  return <>
    <a href="#main" className="skip-to-content">跳转到主要内容</a>
    <div className="shell bc-console">
      <button
        type="button"
        className={`bc-sidebar-backdrop${mobileOpen ? ' is-open' : ''}`}
        aria-label="关闭移动导航"
        tabIndex={mobileOpen ? 0 : -1}
        onClick={closeMobileAndRestoreFocus}
      />
      <aside id="console-sidebar" inert={mobileViewport && !mobileOpen}
        className={`sidebar${mobileOpen ? ' mobile-open' : ''}`}>
        <div className="bc-brand-row">
          <Link href="/overview" className="brand" aria-label="Lanveriq 概览"
            onClick={() => setMobileOpen(false)}>
            <span className="brand-mark" aria-hidden="true"><Layers3 size={24} /></span>
            <span><strong>Lanveriq</strong><small>ENTERPRISE CONSOLE</small></span>
          </Link>
          <button type="button" className="bc-close-nav" aria-label="关闭导航"
            ref={closeButtonRef} onClick={closeMobileAndRestoreFocus}><X size={20} /></button>
        </div>
        <p className="nav-label">WORKSPACE / 工作空间</p>
        <nav className="navigation" aria-label="主导航">
          {consoleRoutes.map(({ href, label, id, planned }) => {
            const Icon = routeIcons[id];
            return <Link key={href} href={href}
              onClick={() => setMobileOpen(false)}
              className={`nav-item${current?.href === href ? ' selected' : ''}`}
              aria-current={current?.href === href ? 'page' : undefined}>
              <Icon size={18} aria-hidden="true" /><span>{label}</span>
              {planned && <span className="bc-nav-planned" aria-label="规划模块">设计</span>}
              {current?.href === href && <ChevronRight size={14} className="nav-chevron" aria-hidden="true"/>}
            </Link>;
          })}
        </nav>
        <div className="sidebar-notice">
          <ShieldCheck size={20} aria-hidden="true" />
          <span><strong>独立安全预览</strong><small>无真实设备、管理员会话或业务数据</small></span>
        </div>
        <p className="bc-sidebar-signoff">LANVERIQ / PRIVATE BY DESIGN</p>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="bc-header-left">
            <button type="button" className="bc-menu-button" ref={menuButtonRef}
              onClick={() => setMobileOpen(true)}
              aria-label="打开导航" aria-expanded={mobileOpen}
              aria-controls="console-sidebar"><Menu size={21} /></button>
            <div className="breadcrumb"><span>工作空间</span>
              <ChevronRight size={15} aria-hidden="true" />
              <strong>{current?.label ?? '管理控制台'}</strong>
            </div>
          </div>
          <div className="top-actions">
            <button type="button" disabled className="bc-global-search"
              aria-label="全局搜索尚未开放">
              <Search size={17} aria-hidden="true" />
              <span>搜索设备、资产或操作</span>
              <kbd>⌘ K</kbd>
            </button>
            <span className="backend-state"><span aria-hidden="true" />业务 API 未连接</span>
            <button type="button" disabled aria-label="通知尚未接入" className="icon-button">
              <Bell size={19} /></button>
            <Link href="/design-system" className="icon-button bc-help-link"
              aria-label="查看设计系统与交互规范"><CircleHelp size={19} /></Link>
            <Link href="/login" className="demo-avatar"
              aria-label="查看尚未开放的管理员登录">DEMO</Link>
          </div>
        </header>
        <main id="main" className="main-content">
          <div className="warning-banner" role="status">
            <span className="bc-preview-dot" aria-hidden="true" />
            <strong>独立预览 · 不含真实数据</strong>
            <span>Next.js App Router 设计预览；未连接 PostgreSQL、Redis、业务 API 或真实终端。</span>
            <span className="bc-preview-label">PREVIEW ONLY</span>
          </div>
          {children}
        </main>
        <footer className="footer">Lanveriq · B+C 设计预览 · 尚未开放真实管理员登录、设备监控或操作</footer>
      </div>
    </div>
  </>;
}
