import { SectionHeading } from '@/components/section-heading';
import { UnconnectedDeviceState } from '@/components/preview-state';

export default function Devices() {
  return <section className="page">
    <div className="title-row"><SectionHeading eyebrow="DEVICES / NXT-01" title="终端资产"
      description="需要通过管理员会话与服务器端设备组授权后，才能取得真实设备列表。" />
      <button disabled className="secondary-action">添加设备（未开放）</button></div>
    <UnconnectedDeviceState />
  </section>;
}
