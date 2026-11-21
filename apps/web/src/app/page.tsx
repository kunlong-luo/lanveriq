import Link from 'next/link';
import { redirect } from 'next/navigation';

// The normal private Next.js runtime keeps the /overview redirect. Only the
// explicitly gated GitHub Pages static export receives a public landing page:
// GitHub Pages cannot execute server redirects or authenticated RSC.
export default function Home() {
  if (process.env.LANVERIQ_PAGES_STATIC_EXPORT === '1') {
    return <main className="login-screen">
      <div className="login-container">
        <div className="login-brand"><strong>Lanveriq</strong></div>
        <section className="login-card" aria-labelledby="pages-preview-title">
          <p className="eyebrow">GITHUB PAGES / STATIC PREVIEW</p>
          <h1 id="pages-preview-title">Lanveriq 管理控制台预览</h1>
          <p className="login-intro">仅展示隔离的 Next.js 页面设计；所有业务功能与真实连接保持关闭。</p>
          <div className="warning-banner" role="status">
            公开静态演示：请勿输入真实账号、密码或终端信息。
          </div>
          <Link className="primary-action" href="/overview">查看只读界面</Link>
          <p className="login-footnote">没有真实登录、数据库、监控、设备注册或 Agent 采集。</p>
        </section>
      </div>
    </main>;
  }
  redirect('/overview');
}
