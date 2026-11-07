import { PlannedConsolePage } from '@/components/planned-console-page';

export default function Reports() {
  return <PlannedConsolePage eyebrow="REPORTING / DESIGN PREVIEW"
    title="报表分析"
    summary="以可追溯的只读报表呈现设备组、连接状态、库存覆盖和证书健康趋势。"
    emptyTitle="尚无经过授权的报表数据"
    emptyDescription="图表保持真实空状态；不绘制虚构百分比、趋势曲线或审计结论。"
    sections={[
      { title: '统计概览', detail: '规划按设备组和时间范围汇总服务端已核实的聚合指标。' },
      { title: '趋势分析', detail: '仅在存在已验证的心跳与库存时间序列时绘制趋势。' },
      { title: '报表导出', detail: '导出需权限、脱敏策略及审计证据，当前不可用。' },
    ]} />;
}
