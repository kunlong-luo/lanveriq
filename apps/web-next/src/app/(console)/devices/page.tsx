import Link from 'next/link';
import { ArrowRight, Monitor, Plus, ShieldCheck } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { DeviceDirectory } from '@/components/device-directory';

/** B+C device directory: no fake rows, no unauthenticated query or action. */
export default function Devices() {
  return <section className="page bc-assets-page">
    <div className="bc-assets-hero">
      <div className="bc-assets-hero-copy">
        <span className="bc-module-tag"><Monitor size={15} aria-hidden="true" /> B + C / 终端资产</span>
        <SectionHeading eyebrow="ASSET DIRECTORY / READ ONLY" title="终端资产"
          description="统一管理设备基础信息、连接状态、注册与库存。任何真实设备数据都必须首先通过管理员身份和设备组权限验证。" />
        <div className="bc-assets-hero-actions">
          <Link href="/enrollment" className="primary-action">
            了解设备接入 <ArrowRight size={16} aria-hidden="true" />
          </Link>
          <button type="button" disabled className="secondary-action">
            <Plus size={16} aria-hidden="true" /> 添加设备（未开放）
          </button>
        </div>
      </div>
      <div className="bc-assets-art" aria-hidden="true">
        <div className="bc-assets-art-square bc-assets-art-square-back" />
        <div className="bc-assets-art-square bc-assets-art-square-mid">
          <Monitor size={76} strokeWidth={1.35} />
        </div>
        <span className="bc-assets-art-badge"><ShieldCheck size={27} /></span>
        <div className="bc-assets-art-orbit" />
      </div>
    </div>
    <div className="bc-section-label">
      <div><span>01 / DEVICE DIRECTORY</span><h2>设备清单</h2></div>
      <span className="bc-aside-note">只读 · 数据范围由服务端授权决定</span>
    </div>
    <DeviceDirectory state={{ kind: 'unconnected' }} />
    <div className="bc-assets-footnote">
      <ShieldCheck size={17} aria-hidden="true" />
      这里的“未连接”并不等于没有设备。搜索和筛选需要在已授权数据返回后才开放。
    </div>
  </section>;
}
