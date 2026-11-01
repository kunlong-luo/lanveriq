import { SectionHeading } from '@/components/section-heading';
import { SafetyGate } from '@/components/preview-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export default function Settings() {
  return <section className="page">
    <SectionHeading eyebrow="SETTINGS / NXT-01" title="系统设置"
      description="技术与安全验收说明；不会更改后端配置或启用任何服务。" />
    <div className="content-grid">
      <Card><CardHeader><CardTitle>候选技术栈</CardTitle></CardHeader><CardContent className="stack-list">
        <p>Next.js 16 App Router + React 19 + TypeScript</p>
        <p>Tailwind CSS 4 + shadcn/ui 兼容组件</p>
        <p>Spring Boot 4 / PostgreSQL 18 / Caddy TLS（业务未连接）</p>
      </CardContent></Card>
      <SafetyGate />
    </div>
  </section>;
}
