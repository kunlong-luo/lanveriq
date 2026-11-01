import { SectionHeading } from '@/components/section-heading';
import { SafetyGate } from '@/components/preview-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

const steps = ['一次性注册凭据', '终端生成密钥及 CSR', '管理员审核证书', 'mTLS 设备绑定'];
export default function Enrollment() {
  return <section className="page">
    <SectionHeading eyebrow="ENROLLMENT / NXT-01" title="设备注册"
      description="展示设计流程，不创建注册凭据、不接入真实设备。" />
    <div className="content-grid"><Card><CardHeader><CardTitle>可信设备接入流程（预览）</CardTitle></CardHeader>
      <CardContent><ol className="step-list">{steps.map((step, index) =>
        <li key={step}><span>{index + 1}</span>{step}</li>)}</ol>
        <button disabled className="primary-action">发起注册（未启用）</button>
      </CardContent></Card><SafetyGate/></div>
  </section>;
}
