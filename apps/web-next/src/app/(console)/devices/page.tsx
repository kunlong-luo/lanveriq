import { Plus } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { DeviceDirectory } from '@/components/device-directory';

export default function Devices() {
  return <section className="page">
    <div className="title-row">
      <SectionHeading eyebrow="DEVICES / NXT-02" title="终端资产"
        description="可按设备名称、系统及设备组进行搜索；只有服务器授予权限后才能显示真实设备。" />
      <button type="button" disabled className="secondary-action"><Plus size={16}/> 添加设备（未开放）</button>
    </div>
    <DeviceDirectory state={{ kind: 'unconnected' }} />
  </section>;
}
