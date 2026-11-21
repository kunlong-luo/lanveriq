import { PlannedConsolePage } from '@/components/planned-console-page';

export default function Policies() {
  return <PlannedConsolePage eyebrow="POLICY MANAGEMENT / DESIGN PREVIEW"
    title="策略管理"
    summary="面向授权设备组的安全策略、规则版本和分阶段生效流程。当前仅展示产品结构。"
    emptyTitle="策略服务尚未接入"
    emptyDescription="不会读取或创建真实安全策略，不向终端下发规则。"
    sections={[
      { title: '策略目录', detail: '规划按状态、所属范围、版本和审批状态筛选策略。' },
      { title: '策略草稿', detail: '预期使用可读规则表单和变更差异预览，而非自由拼接脚本。' },
      { title: '生效与回滚', detail: '先完成 RBAC、审批、签名、审计及回滚验收，再开放实际下发。' },
    ]} />;
}
