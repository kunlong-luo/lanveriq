import Link from 'next/link';
import { Activity, ArrowRight, Fingerprint, LockKeyhole } from 'lucide-react';

// Server-rendered static preview ONLY: no submitted credentials or event handlers.
export function LoginPreview() {
  return <main className="login-screen">
    <div className="login-container">
      <div className="login-brand"><span className="brand-mark"><Activity size={25}/></span>
        <span><strong>Lanveriq</strong><small>MANAGEMENT CONSOLE</small></span></div>
      <section className="login-card" aria-labelledby="login-title">
        <span className="login-icon"><Fingerprint size={24} aria-hidden="true" /></span>
        <p className="eyebrow">ADMIN CONSOLE / NXT-01</p>
        <h1 id="login-title">管理员登录</h1>
        <p className="login-intro">请仅在安全验收完成后使用正式的管理员会话。</p>
        <div className="warning-banner" role="status"><LockKeyhole size={18} aria-hidden="true"/>
          <span>Next.js 演示模式。尚未连接后端，禁止输入真实账号或密码。</span></div>
        <div className="login-fields" aria-label="尚未启用的登录界面">
          <label htmlFor="login-username">管理员账号</label>
          <input id="login-username" type="text" disabled placeholder="尚未开放" autoComplete="off"/>
          <label htmlFor="login-password">密码</label>
          <input id="login-password" type="password" disabled placeholder="尚未开放" autoComplete="off"/>
          <button type="button" disabled className="primary-action">安全登录（未启用） <ArrowRight size={16}/></button>
        </div>
        <p className="login-footnote">正式登录需先通过 HTTPS、CSRF、Cookie、权限及 PostgreSQL 测试。</p>
      </section>
      <Link href="/overview" className="text-link">返回迁移预览 <ArrowRight size={15}/></Link>
    </div>
  </main>;
}
