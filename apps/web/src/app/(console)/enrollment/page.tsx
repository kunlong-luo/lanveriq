import { KeyRound, LockKeyhole, ShieldCheck, WifiOff } from 'lucide-react';
import { SectionHeading } from '@/components/section-heading';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { SafetyGate } from '@/components/preview-state';

const steps = [
  { name: '生成一次性注册凭据', detail: '凭据仅在服务器授权操作后签发，过期与重放必须拒绝。' },
  { name: '终端生成密钥及 CSR', detail: '私钥保留在受管终端，不通过管理后台上传。' },
  { name: '管理员审核设备与证书', detail: '证书身份、组织审批和设备分组分别核验。' },
  { name: 'mTLS 绑定后激活', detail: '验证已授权证书和设备 ID 一致后才能接入。' },
];

export default function Enrollment() {
  return <section className="page">
    <SectionHeading eyebrow="ENROLLMENT / NXT-02" title="设备注册"
      description="可信设备接入流程。尚未开放任何注册凭据创建、证书审批或终端注册接口。" />
    <div className="content-grid">
      <Card><CardHeader>
        <span className="state-icon"><KeyRound size={22} aria-hidden="true"/></span>
        <CardTitle>可信设备接入</CardTitle>
        <CardDescription>所有步骤都需要服务器权限与证书验证，不会通过前端直接执行。</CardDescription>
      </CardHeader>
        <CardContent>
          <ol className="enrollment-steps">{steps.map((step,index)=><li key={step.name}>
            <span className="step-number">{index+1}</span><div><strong>{step.name}</strong><p>{step.detail}</p></div>
          </li>)}</ol>
          <button type="button" className="primary-action" disabled>
            <LockKeyhole size={16}/> 发起注册（未启用）
          </button>
        </CardContent></Card>
      <div className="stacked-panels">
        <SafetyGate />
        <Card><CardHeader><CardTitle>注册服务状态</CardTitle></CardHeader>
          <CardContent className="placeholder-stack">
            <div><WifiOff size={19}/><span>注册业务接口</span><strong>未启用</strong></div>
            <div><ShieldCheck size={19}/><span>证书和审核权限</span><strong>待验收</strong></div>
          </CardContent></Card>
      </div>
    </div>
  </section>;
}
