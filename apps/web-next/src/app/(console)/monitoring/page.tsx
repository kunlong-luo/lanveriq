import { SectionHeading } from '@/components/section-heading';
import { SafetyGate, UnconnectedDeviceState } from '@/components/preview-state';

export default function Monitoring() {
  return <section className="page">
    <SectionHeading eyebrow="MONITORING CENTER / NXT-01" title="监控中心"
      description="计划显示按组授权的健康状态、数据新鲜度和传输缺口，不包含员工屏幕画面。" />
    <div className="content-grid"><UnconnectedDeviceState/><SafetyGate/></div>
  </section>;
}
