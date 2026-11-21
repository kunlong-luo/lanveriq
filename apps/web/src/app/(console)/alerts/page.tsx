import { PlannedConsolePage } from '@/components/planned-console-page';

export default function Alerts() {
  return <PlannedConsolePage eyebrow="ALERT CENTER / DESIGN PREVIEW"
    title="告警中心"
    summary="为终端连接异常、证书状态与库存新鲜度提供统一的告警查看、筛选与审计交互。"
    emptyTitle="告警数据尚未连接"
    emptyDescription="没有授权的业务告警来源；不能据此推断没有异常或事件。"
    sections={[
      { title: '告警筛选', detail: '按严重级别、设备组、处理状态及时间范围查找授权事件。' },
      { title: '告警详情', detail: '查看事件来源、发生时间、影响范围、关联设备与处理建议。' },
      { title: '确认与审计', detail: '需具备角色权限、服务器写入、幂等和审计链后才允许操作。' },
    ]} />;
}
