import { SectionHeading } from '@/components/section-heading';
import { ConnectivityBreakdown, InventoryFreshness, UnconnectedMetrics } from '@/components/health-sections';
import { DeviceDirectory } from '@/components/device-directory';

export default function Monitoring() {
  return <section className="page">
    <SectionHeading eyebrow="MONITORING CENTER / NXT-02" title="监控中心"
      description="关注设备连接、待发送队列、最近心跳与库存覆盖，不展示员工屏幕画面或行为记录。" />
    <UnconnectedMetrics />
    <div className="content-grid">
      <ConnectivityBreakdown />
      <InventoryFreshness />
    </div>
    <DeviceDirectory state={{ kind: 'unconnected' }} />
  </section>;
}
