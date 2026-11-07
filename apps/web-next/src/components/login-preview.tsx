import Link from 'next/link';
import { ArrowRight, Fingerprint, Layers3, LockKeyhole, ShieldCheck } from 'lucide-react';

/** Static design surface: no form, user data, credential capture or calls. */
export function LoginPreview() {
  return <main className="login-screen bc-login-screen">
    <div className="bc-login-frame">
      <aside className="bc-login-intro" aria-label="Lanveriq 品牌介绍">
        <div className="login-brand">
          <span className="brand-mark" aria-hidden="true"><Layers3 size={24}/></span>
          <span><strong>Lanveriq</strong><small>ENTERPRISE CONSOLE</small></span>
        </div>
        <div className="bc-login-intro-copy">
          <span className="bc-module-tag">PRODUCT DESIGN / B + C</span>
          <h2>清晰管理每一台<br/>企业终端</h2>
          <p>统一监控 · 安全管理 · 高效运维</p>
        </div>
        <div className="bc-login-illustration" aria-hidden="true">
          <div className="bc-login-orbit" />
          <div className="bc-login-display"><Layers3 size={49}/><strong>Lanveriq</strong></div>
          <div className="bc-login-shield"><ShieldCheck size={37}/></div>
        </div>
        <p className="bc-login-bottom">LANVERIQ / PRIVATE BY DESIGN</p>
      </aside>
      <section className="login-card bc-login-form" aria-labelledby="login-title">
        <span className="login-icon"><Fingerprint size={24} aria-hidden="true" /></span>
        <p className="eyebrow">ADMIN CONSOLE / DESIGN PREVIEW</p>
        <h1 id="login-title">管理员登录</h1>
        <p className="login-intro">请仅在完成安全验收后使用正式管理员会话。本页面只演示登录界面的视觉与禁用状态。</p>
        <div className="warning-banner" role="status">
          <LockKeyhole size={18} aria-hidden="true" />
          <span>Next.js 独立预览，尚未连接后端，禁止输入真实账号或密码。</span>
        </div>
        <div className="login-fields" aria-label="尚未启用的登录界面">
          <label htmlFor="login-username">管理员账号</label>
          <input id="login-username" type="text" disabled placeholder="尚未开放 · 请勿输入真实账号" autoComplete="off"/>
          <label htmlFor="login-password">密码</label>
          <input id="login-password" type="password" disabled placeholder="尚未开放 · 请勿输入真实密码" autoComplete="off"/>
          <button type="button" disabled className="primary-action">
            安全登录（未启用） <ArrowRight size={16} aria-hidden="true" />
          </button>
        </div>
        <p className="login-footnote">正式登录需要私有 HTTPS 入口、CSRF、Cookie、服务器会话、RBAC 与审计的独立安全验收。</p>
        <Link href="/overview" className="bc-login-explore">
          体验独立设计预览 <ArrowRight size={16} aria-hidden="true" />
        </Link>
      </section>
    </div>
  </main>;
}
