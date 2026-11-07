import { Check, CircleAlert, Info, LockKeyhole, Palette, ShieldCheck } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const palette = [
  { name: 'Brand / 主品牌', value: '#6A5BFF', tone: 'purple' },
  { name: 'Support / 辅助', value: '#3882F6', tone: 'blue' },
  { name: 'Accent / 点缀', value: '#C9FF6A', tone: 'lime' },
  { name: 'Ink / 主文字', value: '#1C1A2E', tone: 'ink' },
  { name: 'Canvas / 页面底色', value: '#F8F8FC', tone: 'canvas' },
  { name: 'Border / 分隔', value: '#EAE8F4', tone: 'border' },
] as const;

export default function DesignSystem() {
  return <section className="page bc-design-system">
    <div className="title-row">
      <SectionHeading eyebrow="DESIGN FOUNDATION / B + C" title="设计系统"
        description="Lanveriq 的统一视觉规范：专业企业信息架构、紫蓝品牌识别与可访问的交互组件。仅供产品设计评审。" />
      <span className="bc-design-version"><Palette size={16} aria-hidden="true" /> DESIGN PREVIEW / V1</span>
    </div>
    <div className="bc-section-label"><div><span>01 / BRAND FOUNDATION</span><h2>品牌色彩</h2></div></div>
    <Card className="bc-token-panel">
      <CardHeader><CardTitle>Color tokens</CardTitle>
        <CardDescription>颜色是语义标记，不用于伪造设备在线或系统安全状态。</CardDescription></CardHeader>
      <CardContent className="bc-swatch-grid">
        {palette.map(item => <div className="bc-swatch" key={item.name}>
          <span className={`bc-swatch-chip bc-swatch-${item.tone}`} aria-hidden="true" />
          <strong>{item.name}</strong><code>{item.value}</code>
        </div>)}
      </CardContent>
    </Card>
    <div className="bc-section-label"><div><span>02 / TYPE + SPACING</span><h2>排版与间距</h2></div></div>
    <div className="bc-token-columns">
      <Card className="bc-token-panel"><CardHeader><CardTitle>Typography</CardTitle>
        <CardDescription>Inter / PingFang SC / Microsoft YaHei / system-ui</CardDescription></CardHeader>
        <CardContent className="bc-type-demo">
          <p className="bc-type-display">统一监控<br/>高效管理</p>
          <p className="bc-type-h2">企业终端资产</p>
          <p className="bc-type-h3">设备连接与库存状态</p>
          <p className="bc-type-body">优先信息可读性、可访问性和准确性，不通过装饰模拟数据。</p>
          <p className="bc-type-small">SECONDARY / 辅助信息</p>
        </CardContent></Card>
      <Card className="bc-token-panel"><CardHeader><CardTitle>Spacing + radius</CardTitle>
        <CardDescription>统一基础单位，避免逐页维护不同尺寸。</CardDescription></CardHeader>
        <CardContent className="bc-spacing-demo">
          {[4,8,12,16,24,32].map(n => <div className="bc-spacing-row" key={n}>
            <span>{n}px</span><i style={{ width: n * 5 }} /></div>)}
          <p>卡片圆角 20px · 按钮 12px · 控件最小触控区域 44px</p>
        </CardContent></Card>
    </div>
    <div className="bc-section-label"><div><span>03 / COMPONENT CONTRACT</span><h2>组件和状态样式</h2></div></div>
    <Card className="bc-token-panel"><CardHeader><CardTitle>按钮、标签与反馈</CardTitle>
      <CardDescription>以下仅展示视觉约定和禁用控件，不是已接入的业务操作。</CardDescription></CardHeader>
      <CardContent className="bc-component-demo">
        <div className="bc-demo-buttons">
          <span className="bc-example-button-primary">主要按钮 <Check size={16} aria-hidden="true" /></span>
          <span className="bc-example-button-secondary">次要按钮</span>
          <button className="secondary-action" type="button" disabled><LockKeyhole size={15} aria-hidden="true"/> 未授权操作</button>
        </div>
        <div className="bc-demo-states" aria-label="纯设计示例状态，非实时设备信息">
          <span className="bc-state-tag is-success"><Check size={14} aria-hidden="true"/> 成功 · 设计样例</span>
          <span className="bc-state-tag is-warning"><CircleAlert size={14} aria-hidden="true"/> 待确认 · 设计样例</span>
          <span className="bc-state-tag is-neutral"><Info size={14} aria-hidden="true"/> 未连接 · 真实预览状态</span>
        </div>
        <div className="bc-design-rule"><ShieldCheck size={19} aria-hidden="true" />
          <span>始终保留可见键盘焦点、缩减动效设置和未授权空状态。所有真实敏感操作必须接受服务端身份和 RBAC 验证。</span></div>
      </CardContent>
    </Card>
  </section>;
}
