import Link from 'next/link';
import { ArrowRight } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { ConnectivityBreakdown, InventoryFreshness, UnconnectedMetrics } from '@/components/health-sections';
import { DeviceDirectory } from '@/components/device-directory';

export default function Overview() {
  return <section className="page">
    <div className="title-row">
      <SectionHeading eyebrow="OVERVIEW / NXT-02" title="设备运行概览"
        description="汇总终端连接、证书与库存健康。当前候选项目未连接业务数据，不展示虚构的设备数量。" />
      <Link className="primary-action" href="/monitoring">查看监控中心 <ArrowRight size={16}/></Link>
    </div>
    <UnconnectedMetrics />
    <div className="content-grid">
      <ConnectivityBreakdown />
      <InventoryFreshness />
    </div>
    <DeviceDirectory state={{ kind: 'unconnected' }} />
  </section>;
}
