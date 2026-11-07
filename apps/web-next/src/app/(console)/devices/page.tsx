import Link from 'next/link';
import { ArrowRight, LockKeyhole, Monitor, Plus, ShieldCheck } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { DeviceDirectory } from '@/components/device-directory';

/**
 * NXT-16: operational-first asset workspace.
 * Hero illustration belongs on /overview, not above the primary table.
 * This screen still has NO authenticated API, credentials or device actions.
 */
export default function Devices() {
  return <section className="page bc-assets-page">
    <header className="bc-assets-workspace-head">
      <div className="bc-assets-workspace-title">
        <span className="bc-module-tag"><Monitor size={15} aria-hidden="true" />
          B + C / 终端资产工作区</span>
        <SectionHeading eyebrow="ASSET DIRECTORY / READ ONLY" title="终端资产"
          description="按已授权设备组查看设备注册、连接与库存状态。当前尚未建立管理员会话，暂不提供真实资产检索。" />
      </div>
      <div className="bc-assets-workspace-actions">
        <span className="bc-assets-access-state"><LockKeyhole size={15} aria-hidden="true" />
          未连接 · 只读预览</span>
        <Link href="/enrollment" className="secondary-action">
          了解接入流程 <ArrowRight size={16} aria-hidden="true" />
        </Link>
        <button type="button" disabled className="primary-action">
          <Plus size={16} aria-hidden="true" /> 添加设备（未开放）
        </button>
      </div>
    </header>
    <div className="bc-section-label">
      <div><span>01 / DEVICE DIRECTORY</span><h2>设备清单</h2></div>
      <span className="bc-aside-note">仅展示经服务端授权的数据范围</span>
    </div>
    <DeviceDirectory state={{ kind: 'unconnected' }} />
    <div className="bc-assets-footnote">
      <ShieldCheck size={17} aria-hidden="true" />
      “未连接”不等于“没有设备”。获得授权之前，搜索、筛选及设备操作均不可使用。
    </div>
  </section>;
}
