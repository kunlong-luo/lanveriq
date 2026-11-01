import { Activity, Clock3, HardDrive, Monitor, ShieldCheck, WifiOff } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const metrics = [
  { name: '已授权设备总数', Icon: Monitor, description: '尚未获取授权设备列表' },
  { name: '在线设备', Icon: Activity, description: '无已验证的心跳汇总' },
  { name: '延迟 / 离线设备', Icon: WifiOff, description: '不可从断开连接推断数量' },
  { name: '已撤销证书', Icon: ShieldCheck, description: '需服务器权限校验' },
] as const;

export function UnconnectedMetrics() {
  return <div className="metric-grid" aria-label="未连接设备指标">
    {metrics.map(({ name, Icon, description }) => <Card key={name} className="metric-card">
      <CardHeader><span className="metric-icon"><Icon size={21} aria-hidden="true"/></span>
        <CardDescription>{name}</CardDescription>
        <CardTitle className="metric-placeholder" aria-label={`${name}：未连接`}>—</CardTitle>
      </CardHeader>
      <CardContent><span className="metric-hint">{description}</span></CardContent>
    </Card>)}
  </div>;
}

export function ConnectivityBreakdown() {
  return <Card className="connectivity-card">
    <CardHeader><CardTitle>设备连接分布</CardTitle>
      <CardDescription>在线、延迟、离线与注册状态须分别由后端统计。</CardDescription></CardHeader>
    <CardContent className="connectivity-content">
      <div className="connectivity-empty-bar" role="img" aria-label="暂无连接状态统计数据" />
      <div className="connectivity-legend">
        {['在线', '延迟', '离线', '已撤销'].map(label => <span key={label}>
          <i aria-hidden="true" /><span>{label}</span><strong>—</strong></span>)}
      </div>
      <p className="freshness-note"><Clock3 size={18} aria-hidden="true"/>
        连接状态、上报时间、库存更新时间应分别展示。暂无数据时不计算健康百分比。</p>
    </CardContent>
  </Card>;
}

export function InventoryFreshness() {
  return <Card>
    <CardHeader><CardTitle>采集与库存覆盖</CardTitle>
      <CardDescription>只读展示待接入的数据新鲜度与队列概览。</CardDescription></CardHeader>
    <CardContent className="placeholder-stack">
      <div><HardDrive size={19} aria-hidden="true"/><span>最新库存快照</span><strong>未连接</strong></div>
      <div><Activity size={19} aria-hidden="true"/><span>心跳接收状态</span><strong>未连接</strong></div>
      <div><Clock3 size={19} aria-hidden="true"/><span>待发送队列统计</span><strong>未连接</strong></div>
    </CardContent>
  </Card>;
}
