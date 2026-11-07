import { CheckCircle2, Clock3, Database, Network, Server, ShieldCheck } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const stack = [
  { name: 'Next.js 16.4.5 App Router / React 19', detail: '独立构建、Chromium 预览和 GitHub Pages 发布已通过' },
  { name: 'Spring Boot 4 / Java 25', detail: '后端单独验收；业务 API 未接入公开页面' },
  { name: 'PostgreSQL 18 / Flyway', detail: '真实数据库集成测试已通过（仅隔离测试）' },
  { name: 'Caddy HTTPS / Spring Security', detail: '合成浏览器会话与双管理员权限隔离通过；生产网络仍待验收' },
];
const gates = [
  { name: 'Next.js 主前端构建与静态演示', passed: true },
  { name: '独立 Chromium 页面、键盘和响应式验收', passed: true },
  { name: 'PostgreSQL 18 测试数据库集成', passed: true },
  { name: 'Caddy → Tomcat 合成双向 TLS 联合验收', passed: true },
  { name: '合成浏览器 Cookie / CSRF / Origin 与分组授权', passed: true },
  { name: '正式管理员登录、私有网络与组织权限验收', passed: false },
  { name: '真实终端与 Agent 上线授权', passed: false },
];
export default function Settings() {
  return <section className="page">
    <SectionHeading eyebrow="SYSTEM / NXT-02" title="系统设置"
      description="工程状态与上线门槛，只读展示。此页面不会修改 PostgreSQL、Redis 或服务器配置。" />
    <div className="content-grid">
      <Card><CardHeader><span className="state-icon"><Server size={22}/></span>
        <CardTitle>技术与连接状态</CardTitle>
        <CardDescription>显示截至 2026-11-07 已核实的自动化测试记录，并非实时连接状态。</CardDescription>
      </CardHeader><CardContent className="stack-list">
        {stack.map(({name,detail},i)=><div className="stack-item" key={name}>
          {i===0?<CheckCircle2 size={19}/>:i===2?<Database size={19}/>:i===3?<Network size={19}/>:<Server size={19}/>}
          <span><strong>{name}</strong><small>{detail}</small></span></div>)}
      </CardContent></Card>
      <Card><CardHeader><span className="state-icon"><ShieldCheck size={22}/></span>
        <CardTitle>发布与安全验收</CardTitle>
        <CardDescription>合成浏览器验收通过不等于生产登录、网络或设备采集获准开放。</CardDescription>
      </CardHeader><CardContent className="gate-checklist">
        {gates.map(({name,passed})=><div key={name}>
          {passed?<CheckCircle2 size={18}/>:<Clock3 size={18}/>}
          <span>{name}</span><strong>{passed?'已通过':'待完成'}</strong></div>)}
        <p className="settings-warning">真实采集、远程操作与屏幕录制均未开放。</p>
      </CardContent></Card>
    </div>
  </section>;
}
