import { Activity, ArrowRight, Fingerprint, KeyRound, LockKeyhole, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router';
import './login.css';

/**
 * WEB-01-AUTH-PREVIEW: No credential collection, session, fetch or storage.
 * This presentation component is intentionally non-operational until the
 * independent Caddy TLS, cookie/CSRF/Origin, PG18 and permission gates pass.
 */
export default function LoginPage() {
  return <main className="login-screen">
    <div className="login-background" aria-hidden="true" />
    <section className="login-container" aria-labelledby="login-title">
      <div className="login-brand"><span className="login-brand-icon"><Activity size={25}/></span>
        <span><strong>Lanveriq</strong><small>MANAGEMENT CONSOLE</small></span></div>
      <div className="login-card">
        <span className="login-auth-icon"><Fingerprint size={25}/></span>
        <p className="eyebrow">ADMIN CONSOLE / V0.1</p>
        <h1 id="login-title">管理员登录</h1>
        <p className="login-intro">使用获得授权的管理员账号登录后，才能访问所属设备组的健康与资产信息。</p>
        <div className="login-preview-banner" role="status"><LockKeyhole size={18}/>
          <span><strong>演示模式 · 全部为合成数据</strong><br/>未连接后端。登录和会话功能尚未开放，请勿输入真实账号或密码。</span>
        </div>
        <form className="login-form" onSubmit={event => event.preventDefault()} aria-label="登录表单（未启用）">
          <label htmlFor="preview-account">管理员账号</label>
          <div className="login-input-wrap"><KeyRound size={18}/><input id="preview-account" type="text" placeholder="待启用" disabled autoComplete="off"/></div>
          <label htmlFor="preview-password">密码</label>
          <div className="login-input-wrap"><ShieldCheck size={18}/><input id="preview-password" type="password" placeholder="待启用" disabled autoComplete="off"/></div>
          <button type="submit" className="login-submit" disabled aria-disabled="true">安全登录（尚未开放） <ArrowRight size={17}/></button>
        </form>
        <div className="login-footer"><LockKeyhole size={16}/><span>正式登录需通过 CSRF、Origin、Cookie、权限和数据库安全验收</span></div>
      </div>
      <Link to="/overview" className="login-back">返回合成数据控制台预览 <ArrowRight size={16}/></Link>
      <p className="login-credit">Lanveriq · 本地 UI 预览，不进行任何设备监控</p>
    </section>
  </main>;
}
