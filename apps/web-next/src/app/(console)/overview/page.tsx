import Link from 'next/link';
import { Activity, Monitor, Server, ShieldCheck } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { SectionHeading } from '@/components/section-heading';
import { SafetyGate, UnconnectedDeviceState } from '@/components/preview-state';

const metrics = [
  { title: '已授权设备', icon: Monitor },
  { title: '在线设备', icon: Activity },
  { title: '待处理事项', icon: Server },
  { title: '证书状态', icon: ShieldCheck },
];
export default function Overview() {
  return <section className="page">
    <div className="title-row"><SectionHeading eyebrow="OVERVIEW / NXT-01" title="设备运行概览"
      description="新的 Next.js 管理后台候选；仅展示布局和受控的未连接状态。" />
      <Link href="/devices" className="primary-action">查看终端资产 →</Link></div>
    <div className="metric-grid">{metrics.map(({ title, icon: Icon }) =>
      <Card key={title} className="metric-card"><CardHeader>
        <span className="metric-icon"><Icon size={21} aria-hidden="true" /></span>
        <CardDescription>{title}</CardDescription>
        <CardTitle className="metric-placeholder">—</CardTitle>
      </CardHeader><CardContent><span className="metric-hint">尚无已授权业务数据</span></CardContent></Card>)}</div>
    <div className="content-grid"><UnconnectedDeviceState/><SafetyGate/></div>
  </section>;
}
