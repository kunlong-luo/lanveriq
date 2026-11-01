import Link from 'next/link';
import { notFound } from 'next/navigation';
import { Clock3, HardDrive, RefreshCcw } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { DeviceDetailTabs } from '@/components/device-detail-tabs';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const summary = [
  { label: '最后心跳', Icon: Clock3 },
  { label: '最新资产快照', Icon: HardDrive },
  { label: '待发送队列', Icon: RefreshCcw },
];

export default async function DeviceDetail({ params }: Readonly<{ params: Promise<{ id: string }> }>) {
  const { id } = await params;
  // Reject invalid path input before rendering any detail; UUID alone never
  // grants permission, and this page deliberately does no server-side reads.
  if (!uuid.test(id)) notFound();
  return <section className="page">
    <Link href="/devices" className="text-link">← 返回终端资产</Link>
    <div className="title-row">
      <SectionHeading eyebrow="DEVICE DETAIL / NXT-02" title="设备详情（未连接）"
        description="此标识符仅通过格式检查，当前会话没有经过服务器设备组授权，不代表设备存在。" />
      <span className="read-only-pill">只读 · 未连接</span>
    </div>
    <div className="detail-summary-grid">{summary.map(({label, Icon}) => (
      <div className="detail-summary-item" key={label}><Icon size={19} aria-hidden="true" />
        <small>{label}</small><strong>未连接</strong></div>
    ))}</div>
    <DeviceDetailTabs />
    <section className="disabled-actions" aria-label="不可用的设备操作">
      <div><strong>设备操作（暂不可用）</strong>
        <p>刷新资产及撤销证书必须通过授权、审计及独立安全验收。</p></div>
      <button className="secondary-action" type="button" disabled>申请刷新资产</button>
      <button className="danger-action" type="button" disabled>撤销证书</button>
    </section>
  </section>;
}
