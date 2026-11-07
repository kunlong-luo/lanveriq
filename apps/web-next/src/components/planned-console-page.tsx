import Link from 'next/link';
import { ArrowRight, LockKeyhole, ShieldCheck, Sparkles, WifiOff } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from './ui/card';

type PlannedSection = Readonly<{ title: string; detail: string }>;

export type PlannedConsolePageProps = Readonly<{
  eyebrow: string;
  title: string;
  summary: string;
  sections: readonly PlannedSection[];
  emptyTitle: string;
  emptyDescription: string;
}>;

/**
 * Honest placeholder for B+C modules that have NOT passed live API/RBAC
 * acceptance. This is visual information architecture, not a business UI.
 * No synthetic records, browser storage, credentials or network calls.
 */
export function PlannedConsolePage({
  eyebrow, title, summary, sections, emptyTitle, emptyDescription,
}: PlannedConsolePageProps) {
  return <section className="page bc-planned-page">
    <div className="bc-module-hero">
      <span className="bc-module-tag"><Sparkles size={14} aria-hidden="true" /> B+C DESIGN / 规划模块</span>
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      <p className="bc-module-description">{summary}</p>
      <div className="bc-module-notice"><ShieldCheck size={17} aria-hidden="true" />
        此页面用于评审交互结构，尚未接入真实 API、身份权限或设备数据。</div>
    </div>
    <div className="bc-section-label">
      <div><span>01 / DATA BOUNDARY</span><h2>数据与访问状态</h2></div>
    </div>
    <Card className="bc-planned-empty">
      <CardContent>
        <span className="bc-planned-icon"><WifiOff size={26} aria-hidden="true" /></span>
        <h2>{emptyTitle}</h2>
        <p>{emptyDescription}</p>
        <span className="bc-readonly-pill"><LockKeyhole size={15} aria-hidden="true" /> 未连接 · 不展示假数据</span>
      </CardContent>
    </Card>
    <div className="bc-section-label">
      <div><span>02 / PRODUCT STRUCTURE</span><h2>预定功能结构</h2></div>
      <span className="bc-aside-note">仅展示设计意向，不代表功能已上线</span>
    </div>
    <div className="bc-planned-cards">
      {sections.map((item, index) => <Card key={item.title} className="bc-feature-card">
        <CardHeader>
          <span className="bc-feature-number">0{index + 1}</span>
          <CardTitle>{item.title}</CardTitle>
          <CardDescription>{item.detail}</CardDescription>
        </CardHeader>
      </Card>)}
    </div>
    <div className="bc-module-next">
      <div><strong>下一步：以设计评审决定权限、状态和业务交互</strong>
        <p>真实功能须经过服务端权限审查、接口契约与独立安全验收。</p></div>
      <Link href="/overview" className="secondary-action">返回概览 <ArrowRight size={15} aria-hidden="true" /></Link>
    </div>
  </section>;
}
