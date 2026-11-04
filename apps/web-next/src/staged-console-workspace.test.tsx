import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StagedConsoleWorkspace } from './staged-console-workspace';
import { DeviceDirectory } from './components/device-directory';

describe('NXT-06-A product-shaped Console candidate (unmounted)', () => {
  it('renders inert test-only login with no business requests or data in SSR', () => {
    const html = renderToStaticMarkup(<StagedConsoleWorkspace />);
    expect(html).toContain('只读管理控制台候选');
    expect(html).toContain('禁止输入真实管理员凭据');
    expect(html).toContain('data-candidate-status="signed_out"');
    expect(html).toContain('id="candidate-username"');
    expect(html).toContain('id="candidate-password"');
    expect(html).toContain('type="password"');
    expect(html).toContain('autoComplete="new-password"');
    expect(html).toContain('安全登录并获取授权设备');
    expect(html).not.toContain('SyntheticBrowserA-');
    expect(html).not.toContain('synthetic-browser-a');
    expect(html).not.toContain('已授权设备总数');
    expect(html).not.toContain('action="/api');
    expect(html).not.toContain('method="post"');
    expect(html).not.toContain('自动刷新');
  });

  it('disables navigation to inert detail pages while rendering authorized rows', () => {
    const rows = [{
      id: '00000000-0000-7000-8000-000000000101',
      label: 'SyntheticBrowserA-1',
      group: 'SyntheticQA',
      os: null,
      enrollment: 'active' as const,
      connectivity: 'unknown' as const,
      heartbeat: null,
      queue: null,
    }];
    const html = renderToStaticMarkup(<DeviceDirectory
      state={{ kind: 'ready', rows, hasMore: false }}
      allowDetailNavigation={false}/>);
    expect(html).toContain('SyntheticBrowserA-1');
    expect(html).toContain('详情未开放');
    expect(html).not.toContain('href="/devices/');
  });
});
