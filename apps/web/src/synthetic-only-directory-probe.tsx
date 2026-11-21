'use client';

import { useState } from 'react';
import { DeviceDirectory, type DirectoryRow, type DirectoryState } from './components/device-directory';

/**
 * NXT-17 CI-ONLY interactive fixture. No App Router route imports this module.
 * It is mounted only by scripts/install-synthetic-directory-fixture.mjs in a
 * disposable GitHub Actions runner under an explicit opt-in environment gate.
 * No network/auth/backend/database, no real device identity or user data.
 */
const testPageAlpha: readonly DirectoryRow[] = [
  { id:'00000000-0000-7000-8000-000000000021', label:'CI合成设备甲',
    os:'Windows synthetic', group:'CI测试组甲',
    enrollment:'active', connectivity:'unknown', heartbeat:null, queue:null },
  { id:'00000000-0000-7000-8000-000000000022', label:'CI合成设备乙',
    os:'Linux synthetic', group:'CI测试组乙',
    enrollment:'pending_certificate', connectivity:'unknown', heartbeat:null, queue:null },
];
const testPageBeta: readonly DirectoryRow[] = [
  { id:'00000000-0000-7000-8000-000000000023', label:'CI合成设备丙',
    os:'Linux synthetic', group:'CI测试组丙',
    enrollment:'active', connectivity:'unknown', heartbeat:null, queue:null },
];

const authorized = (rows: readonly DirectoryRow[]): DirectoryState =>
  ({ kind:'ready', rows, hasMore:false });

export function SyntheticDirectoryProbe() {
  const [directory, setDirectory] = useState<DirectoryState>(() => authorized(testPageAlpha));
  return <main className="bc-console" style={{ width:'100%', padding:20, margin:'0 auto' }}>
    <header style={{ marginBottom:18 }}>
      <h1>CI合成设备目录交互验收</h1>
      <p>一次性隔离测试路由。以下记录为固定合成数据，不连接任何真实服务。</p>
      <div style={{ display:'flex', gap:12, flexWrap:'wrap' }}>
        <button type="button" onClick={() => setDirectory(authorized(testPageAlpha))}>
          恢复合成授权页甲
        </button>
        <button type="button" onClick={() => setDirectory(authorized(testPageBeta))}>
          切换合成授权页乙
        </button>
        <button type="button" onClick={() => setDirectory({kind:'forbidden'})}>
          模拟权限撤销
        </button>
      </div>
    </header>
    <DeviceDirectory state={directory} allowDetailNavigation={false} />
  </main>;
}
