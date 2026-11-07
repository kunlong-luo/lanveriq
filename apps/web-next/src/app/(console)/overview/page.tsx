import Link from 'next/link';
import { ArrowRight, ArrowUpRight, ShieldCheck, Sparkles } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import {
  ActivityTrend, ConnectivityBreakdown, InventoryFreshness, UnconnectedMetrics,
} from '@/components/health-sections';
import { DeviceDirectory } from '@/components/device-directory';

/**
 * NXT-14: B+C approved standalone prototype translated to real App Router
 * components. Artwork is original CSS, never a chart of measured data.
 * No dynamic fixtures, external media, credentials, API or cookies.
 */
export default function Overview() {
  return <section className="page overview-page bc-overview">
    <div className="overview-hero">
      <div className="overview-hero-content">
        <div className="hero-topline">
          <span className="bc-hero-tag"><Sparkles size={14} aria-hidden="true" /> B + C 设计融合</span>
          <span className="bc-hero-tag bc-hero-tag-lime"><ShieldCheck size={14} aria-hidden="true" /> 未连接真实数据</span>
        </div>
        <SectionHeading eyebrow="LANVERIQ / CONTROL CENTER" title="设备运行概览"
          description="企业终端与 IT 资产管理工作区。统一梳理连接状态、库存健康及安全边界，为未来正式数据接入建立清晰的管理体验。" />
        <p className="bc-hero-slogan">统一监控 · 安全管理 · 高效运维</p>
        <div className="hero-actions">
          <Link className="hero-primary-action" href="/devices">
            查看终端资产 <ArrowUpRight size={17} aria-hidden="true" />
          </Link>
          <Link className="hero-text-action" href="/monitoring">
            查看监控中心 <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>
      </div>
      <div className="hero-artwork" aria-hidden="true">
        <div className="bc-illustration-glow" />
        <div className="bc-illustration-orbit" />
        <div className="bc-illustration-platform" />
        <div className="bc-illustration-laptop"><div className="bc-laptop-screen">
          <span className="bc-laptop-mark" /><strong>Lanveriq</strong>
        </div></div>
        <div className="bc-illustration-keyboard" />
        <div className="bc-illustration-shield"><ShieldCheck size={37} /></div>
        <div className="bc-illustration-server"><span/><span/><span/></div>
        <div className="bc-illustration-float"><span/><span/><span/></div>
      </div>
    </div>
    <div className="overview-section-title">
      <div><span className="section-index">01 / OVERVIEW</span><h2>工作区概览</h2></div>
      <span className="section-aside">指标需要真实授权数据 · 当前不显示虚构统计</span>
    </div>
    <UnconnectedMetrics />
    <div className="overview-section-title">
      <div><span className="section-index">02 / INSIGHTS</span><h2>连接与数据新鲜度</h2></div>
      <span className="section-aside">仅由服务端核实后展示分组统计和趋势</span>
    </div>
    <div className="bc-insight-grid">
      <ConnectivityBreakdown />
      <ActivityTrend />
      <InventoryFreshness />
    </div>
    <div className="overview-section-title">
      <div><span className="section-index">03 / ASSETS</span><h2>终端资产</h2></div>
      <span className="section-aside">权限验证前不展示任何设备记录</span>
    </div>
    <DeviceDirectory state={{ kind: 'unconnected' }} />
  </section>;
}
