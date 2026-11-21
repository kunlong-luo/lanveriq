'use client';

import { Tabs } from '@base-ui/react/tabs';
import { Activity, Cpu, HardDrive, LockKeyhole, Network } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription } from './ui/card';

const tabs = [
  { id: 'hardware', label: '硬件', Icon: Cpu },
  { id: 'network', label: '网络', Icon: Network },
  { id: 'health', label: '健康', Icon: Activity },
] as const;

type TabId = (typeof tabs)[number]['id'];

const descriptions: Record<TabId, { title: string; message: string }> = {
  hardware: {
    title: '硬件资产未连接',
    message: 'CPU、内存、磁盘、操作系统均需来自通过授权的库存快照，不能使用占位硬件参数。',
  },
  network: {
    title: '网络信息受限',
    message: 'IP、MAC、网卡等敏感字段需要后端按设备组与数据类别分别授权，当前不显示。',
  },
  health: {
    title: '尚无可信健康证据',
    message: '未收到已授权的服务器心跳与采集覆盖信息，不能推断设备在线或离线。',
  },
};

/**
 * NXT-15: Base UI owns the WAI-ARIA tabs, roving focus and arrow-key
 * activation. Only visual classes and safe read-only messages live here.
 * No data fetch, browser storage or inferred device health.
 */
export function DeviceDetailTabs() {
  return <Card className="detail-tabs-card">
    <Tabs.Root defaultValue="hardware" className="bc-device-tabs">
      <CardHeader><CardTitle>设备资产与健康</CardTitle>
        <CardDescription>这些标签页仅提供正式只读详情页的布局。</CardDescription>
      </CardHeader>
      <Tabs.List className="detail-tab-list" aria-label="设备详情分类" activateOnFocus>
        {tabs.map(({ id, label, Icon }) =>
          <Tabs.Tab key={id} value={id}
            className={state => state.active ? 'detail-tab selected' : 'detail-tab'}>
            <Icon size={17} aria-hidden="true"/>{label}
          </Tabs.Tab>)}
      </Tabs.List>
      {tabs.map(({ id }) => {
        const detail = descriptions[id];
        return <Tabs.Panel key={id} value={id} className="detail-tab-content">
          <span className="state-icon" aria-hidden="true">
            {id === 'network' ? <LockKeyhole size={22}/> :
              id === 'hardware' ? <HardDrive size={22}/> : <Activity size={22}/>}
          </span>
          <strong>{detail.title}</strong><p>{detail.message}</p>
        </Tabs.Panel>;
      })}
    </Tabs.Root>
  </Card>;
}
