import Link from 'next/link';
import { ArrowRight, ArrowUpRight, Sparkles } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { ConnectivityBreakdown, InventoryFreshness, UnconnectedMetrics } from '@/components/health-sections';
import { DeviceDirectory } from '@/components/device-directory';

/**
 * NXT-13 visual layer: Craftwork-inspired editorial type, bento spacing and
 * abstract CSS-only art. The illustration is not a chart or live signal.
 * The page NEVER requests credentials, devices, health or monitoring data.
 */
export default function Overview() {
  return <section className="page overview-page">
    <div className="overview-hero">
      <div className="overview-hero-content">
        <div className="hero-topline">
          <span className="hero-kicker-icon"><Sparkles size={14} aria-hidden="true" /></span>
          <span>LANVERIQ / WORKSPACE</span>
          <span className="hero-preview-tag">DESIGN PREVIEW</span>
        </div>
        <SectionHeading eyebrow="OVERVIEW / NXT-13" title="设备运行概览"
          description="以更清晰的视角了解终端连接、证书和库存健康。当前仍为安全隔离预览，所有指标均等待授权数据接入。" />
        <div className="hero-actions">
          <Link className="hero-primary-action" href="/monitoring">
            探索监控中心 <ArrowUpRight size={17} aria-hidden="true" />
          </Link>
          <Link className="hero-text-action" href="/devices">
            查看终端资产 <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>
      </div>
      <div className="hero-artwork" aria-hidden="true">
        <div className="hero-art-grid" />
        <div className="hero-art-orbit hero-art-orbit-outer" />
        <div className="hero-art-orbit hero-art-orbit-inner" />
        <div className="hero-art-core"><span /></div>
        <div className="hero-art-marker hero-art-marker-a" />
        <div className="hero-art-marker hero-art-marker-b" />
        <div className="hero-art-note"><span>VISUAL SYSTEM</span><strong>01 / 03</strong></div>
      </div>
    </div>

    <div className="overview-section-title">
      <div><span className="section-index">01 / STATUS</span><h2>工作区一览</h2></div>
      <span className="section-aside">暂无已授权数据 · 不生成演示指标</span>
    </div>
    <UnconnectedMetrics />

    <div className="overview-section-title">
      <div><span className="section-index">02 / INSIGHTS</span><h2>连接与数据新鲜度</h2></div>
      <span className="section-aside">分组状态仅以服务端结果为准</span>
    </div>
    <div className="content-grid">
      <ConnectivityBreakdown />
      <InventoryFreshness />
    </div>

    <div className="overview-section-title">
      <div><span className="section-index">03 / ASSETS</span><h2>终端资产</h2></div>
      <span className="section-aside">权限验证前不显示设备信息</span>
    </div>
    <DeviceDirectory state={{ kind: 'unconnected' }} />
  </section>;
}
