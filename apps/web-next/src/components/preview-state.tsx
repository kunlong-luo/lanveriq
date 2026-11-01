import { LockKeyhole, ShieldCheck, WifiOff } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './ui/card';

/**
 * NXT-01: no network requests, props from server, sensitive fields or fixtures.
 * This is not proof that the authorized PostgreSQL device table is empty.
 */
export function UnconnectedDeviceState() {
  return <Card className="preview-empty" role="status">
    <CardHeader>
      <span className="state-icon"><WifiOff size={22} aria-hidden="true" /></span>
      <CardTitle>设备数据尚未连接</CardTitle>
      <CardDescription>当前为 Next.js 独立迁移预览，不代表数据库中没有设备。</CardDescription>
    </CardHeader>
    <CardContent className="state-body">
      <p>管理员会话、分组授权及真实资产接口尚未开放。不会填充虚构设备，也不会自动轮询。</p>
    </CardContent>
  </Card>;
}

export function SafetyGate() {
  return <Card className="gate-card"><CardHeader>
    <span className="state-icon"><LockKeyhole size={22} aria-hidden="true" /></span>
    <CardTitle>业务访问默认关闭</CardTitle>
    <CardDescription>真实登录及设备操作需经过独立安全验收。</CardDescription>
  </CardHeader><CardContent className="gate-list">
    <span><ShieldCheck size={17} aria-hidden="true" /> PostgreSQL 18 真实集成测试</span>
    <span><ShieldCheck size={17} aria-hidden="true" /> Caddy HTTPS、Cookie、CSRF、Origin</span>
    <span><ShieldCheck size={17} aria-hidden="true" /> Spring Security 按组授权</span>
  </CardContent></Card>;
}
