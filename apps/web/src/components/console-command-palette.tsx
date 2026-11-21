'use client';

import { useEffect, useState } from 'react';
import { Dialog } from '@base-ui/react/dialog';
import Link from 'next/link';
import { ArrowUpRight, Search, X } from 'lucide-react';
import { findConsolePages } from '@/lib/console-navigation';

/**
 * NXT-22: local-only navigation search over the fixed public route allowlist.
 * It never searches devices, opens privileged routes, reads session state,
 * persists text, or sends requests. Actual business search remains disabled.
 */
export function ConsoleCommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const matches = findConsolePages(query);

  useEffect(() => {
    const onShortcut = (event: KeyboardEvent) => {
      if (event.repeat || event.altKey || !(event.metaKey || event.ctrlKey)
          || event.key.toLowerCase() !== 'k') return;
      event.preventDefault();
      setOpen(true);
    };
    document.addEventListener('keydown', onShortcut);
    return () => document.removeEventListener('keydown', onShortcut);
  }, []);

  const close = () => { setOpen(false); setQuery(''); };

  return <Dialog.Root open={open} onOpenChange={next => {
    setOpen(next);
    if (!next) setQuery('');
  }}>
    <Dialog.Trigger type="button" className="bc-global-search"
      aria-label="打开工作空间搜索" aria-keyshortcuts="Control+K Meta+K">
      <Search size={17} aria-hidden="true" />
      <span>搜索页面与工作空间</span><kbd>⌘ K</kbd>
    </Dialog.Trigger>
    <Dialog.Portal>
      <Dialog.Backdrop className="bc-base-dialog-backdrop" />
      <Dialog.Popup className="bc-command-dialog">
        <div className="bc-command-heading">
          <div>
            <Dialog.Title>快速导航</Dialog.Title>
            <Dialog.Description>只搜索当前预览中的页面；不查询真实设备或业务数据。</Dialog.Description>
          </div>
          <Dialog.Close type="button" className="bc-command-close" aria-label="关闭快速导航">
            <X size={19} aria-hidden="true" />
          </Dialog.Close>
        </div>
        <div className="bc-command-input-row">
          <Search size={19} aria-hidden="true" />
          <input autoFocus type="search" value={query} maxLength={80}
            onChange={event => setQuery(event.target.value)}
            aria-label="搜索工作空间页面" placeholder="输入页面名称，如：终端资产、策略…" />
        </div>
        <nav className="bc-command-results" aria-label="快速导航结果">
          {matches.length ? matches.map(route =>
            <Link key={route.href} href={route.href} onClick={close}
              className="bc-command-result">
              <span className="bc-command-result-label">{route.label}</span>
              <span className="bc-command-result-state">
                {route.planned ? '设计预览' : '页面预览'}
              </span>
              <ArrowUpRight size={16} aria-hidden="true" />
            </Link>
          ) : <p className="bc-command-empty" role="status">
            没有匹配的页面。真实设备、账号及业务搜索尚未开放。
          </p>}
        </nav>
        <p className="bc-command-footer">按 Esc 关闭 · 仅在本地页面导航 · 无业务请求</p>
      </Dialog.Popup>
    </Dialog.Portal>
  </Dialog.Root>;
}
