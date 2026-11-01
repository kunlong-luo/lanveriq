import Link from 'next/link';
import { notFound } from 'next/navigation';
import { SectionHeading } from '@/components/section-heading';
import { UnconnectedDeviceState } from '@/components/preview-state';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function DeviceDetail({ params }: Readonly<{
  params: Promise<{ id: string }>;
}>) {
  const { id } = await params;
  if (!uuid.test(id)) notFound();
  return <section className="page">
    <Link href="/devices" className="text-link">← 返回终端资产</Link>
    <SectionHeading eyebrow="DEVICE DETAIL / NXT-01" title="设备详情（未连接）"
      description="合法设备标识不等于当前管理员拥有读取权限。此页面不会根据 ID 直接获取任何数据。" />
    <UnconnectedDeviceState />
  </section>;
}
