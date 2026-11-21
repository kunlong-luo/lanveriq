import { SectionHeading } from '@/components/section-heading';
import {
  ActivityTrend, ConnectivityBreakdown, InventoryFreshness, UnconnectedMetrics,
} from '@/components/health-sections';
import { DeviceDirectory } from '@/components/device-directory';

/** Only read-only layout until explicitly approved authenticated adapter. */
export default function Monitoring() {
  return <section className="page bc-monitoring-page">
    <SectionHeading eyebrow="MONITORING CENTER / B + C" title="监控中心"
      description="设备连接状态、心跳数据新鲜度和资产快照的专业只读工作区。不采集屏幕、按键或员工行为。" />
    <div className="bc-section-label">
      <div><span>01 / CONNECTION</span><h2>连接健康</h2></div>
      <span className="bc-aside-note">没有授权数据，不展示伪造监控指标</span>
    </div>
    <UnconnectedMetrics />
    <div className="bc-section-label"><div>
      <span>02 / ACTIVITY</span><h2>状态和活动</h2>
    </div></div>
    <div className="bc-insight-grid">
      <ConnectivityBreakdown />
      <ActivityTrend />
      <InventoryFreshness />
    </div>
    <div className="bc-section-label"><div>
      <span>03 / DEVICE STATUS</span><h2>终端健康列表</h2>
    </div></div>
    <DeviceDirectory state={{ kind: 'unconnected' }} />
  </section>;
}
