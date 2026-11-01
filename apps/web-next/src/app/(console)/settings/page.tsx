import { CheckCircle2, Clock3, Database, Network, Server, ShieldCheck } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const stack = [
  { name: 'Next.js 16 App Router / React 19', detail: 'NXT-01 构建与路由检查通过' },
  { name: 'Spring Boot 4 / Java 25', detail: '业务 API 未连接到此预览' },
  { name: 'PostgreSQL 18 / Flyway', detail: '已通过 58/58 项真实数据库集成测试（仅测试环境）' },
  { name: 'Caddy HTTPS / Spring Security', detail: 'Agent 合成 TLS 联合验收通过；Console 浏览器会话尚待验收' },
];
const gates = [
  { name: 'Next.js 候选项目构建', passed: true },
  { name: '完整页面迁移与可访问性检查', passed: false },
  { name: 'PostgreSQL 18 全量集成测试', passed: true },
  { name: 'Caddy → Tomcat 合成双向 TLS 联合验收', passed: true },
  { name: 'Console 浏览器 HTTPS、Cookie、CSRF、Origin', passed: false },
  { name: '按设备组授权的真实 API 联调', passed: false },
];
export default function Settings() {
  return <section className="page">
    <SectionHeading eyebrow="SYSTEM / NXT-02" title="系统设置"
      description="工程状态与上线门槛，只读展示。此页面不会修改 PostgreSQL、Redis 或服务器配置。" />
    <div className="content-grid">
      <Card><CardHeader><span className="state-icon"><Server size={22}/></span>
        <CardTitle>技术与连接状态</CardTitle>
        <CardDescription>显示截至 2026-11-01 已核实的 CI 记录，并非实时连接状态。</CardDescription>
      </CardHeader><CardContent className="stack-list">
        {stack.map(({name,detail},i)=><div className="stack-item" key={name}>
          {i===0?<CheckCircle2 size={19}/>:i===2?<Database size={19}/>:i===3?<Network size={19}/>:<Server size={19}/>}
          <span><strong>{name}</strong><small>{detail}</small></span></div>)}
      </CardContent></Card>
      <Card><CardHeader><span className="state-icon"><ShieldCheck size={22}/></span>
        <CardTitle>发布与安全验收</CardTitle>
        <CardDescription>自动化构建通过不等于可以启用真实设备功能。</CardDescription>
      </CardHeader><CardContent className="gate-checklist">
        {gates.map(({name,passed})=><div key={name}>
          {passed?<CheckCircle2 size={18}/>:<Clock3 size={18}/>}
          <span>{name}</span><strong>{passed?'已通过':'待完成'}</strong></div>)}
        <p className="settings-warning">真实采集、远程操作与屏幕录制均未开放。</p>
      </CardContent></Card>
    </div>
  </section>;
}
