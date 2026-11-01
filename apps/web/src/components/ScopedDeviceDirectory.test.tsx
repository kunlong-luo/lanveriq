import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  ScopedDeviceDirectory,
  safeScopedDeviceHref,
  type ScopedDeviceDirectoryState,
} from './ScopedDeviceDirectory';

const render = (state: ScopedDeviceDirectoryState) =>
  renderToStaticMarkup(<ScopedDeviceDirectory state={state} />);

describe('FE-02-B authorized read-only directory: render-only, never fetch', () => {
  it('renders a genuine empty authorized page without demo device fallback', () => {
    const html = render({ kind: 'ready', rows: [], hasMore: false });
    expect(html).toContain('当前授权范围内没有设备');
    expect(html).toContain('不会填充演示终端');
    expect(html).not.toContain('演示设备 A');
    expect(html).not.toContain('<tbody>');
    expect(html).not.toContain('添加设备');
  });

  it.each([
    ['loading', '正在加载已授权设备'],
    ['signed_out', '登录状态已失效'],
    ['forbidden', '没有设备访问权限'],
    ['throttled', '请求受到限制'],
    ['unavailable', '设备服务暂不可用'],
  ] as const)('renders %s without showing any rows or old counts', (kind, message) => {
    const html = render({ kind });
    expect(html).toContain(message);
    expect(html).not.toContain('<table');
    expect(html).not.toContain('演示设备 A');
    expect(html).not.toContain('自动刷新');
  });

  it('renders only supplied display-only fields, without a device mutation', () => {
    const deviceId = '00000000-0000-7000-8000-000000000001';
    const html = render({
      kind: 'ready',
      hasMore: true,
      rows: [{
        deviceId,
        title: '测试终端 & <escape>',
        subtitle: 'Windows · Agent 0.1',
        groupLabel: '合成测试组',
        connectivityLabel: '未知（无心跳）',
        lastSeenLabel: '尚无记录',
        queueLabel: '未上报',
      }],
    });
    expect(html).toContain('测试终端 &amp; &lt;escape&gt;');
    expect(html).toContain(`href="/devices/${deviceId}"`);
    expect(html).toContain('未知（无心跳）');
    expect(html).toContain('还有后续设备页');
    expect(html).not.toContain('撤销证书');
    expect(html).not.toContain('type="submit"');
    expect(html).not.toContain('/api/v1/');
  });

  it('does not form links from an unvalidated server-returned device ID', () => {
    for (const deviceId of [
      '../auth/logout', '%2fapi%2fv1%2f', 'https://other.example/',
      'not-a-uuid', '', '00000000-0000-0000-0000-000000000000?x=1',
    ]) {
      expect(safeScopedDeviceHref(deviceId)).toBeNull();
      const html = render({
        kind: 'ready', hasMore: false,
        rows: [{ deviceId, title: 'untrusted', subtitle: '',
          groupLabel: '', connectivityLabel: '', lastSeenLabel: '', queueLabel: '' }],
      });
      expect(html).not.toContain('href=');
    }
  });

  it('does not import fixture data and is safe to render without a browser', () => {
    const html = render({ kind: 'ready', rows: [], hasMore: true });
    expect(html).toContain('还有后续设备页');
    expect(html).not.toContain('5 台');
  });
});
