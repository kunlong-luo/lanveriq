import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { SyntheticMultiAdminProbe } from './synthetic-only-multi-admin-probe';

describe('manual-only nonempty two-group Next.js browser fixture', () => {
  it('is inert on SSR and contains no authorized inventory before browser login', () => {
    const html = renderToStaticMarkup(<SyntheticMultiAdminProbe />);
    expect(html).toContain('双管理员合成隔离测试');
    expect(html).toContain('禁止真实账号');
    expect(html).toContain('data-state="idle"');
    expect(html).toContain('data-cache-count="0"');
    expect(html).toContain('type="password"');
    expect(html).toContain('autoComplete="new-password"');
    expect(html).toContain('id="multi-admin-device-list"');
    expect(html).toContain('disabled');
    expect(html).not.toContain('SyntheticBrowserA-1');
    expect(html).not.toContain('SyntheticBrowserB-1');
    expect(html).not.toContain('value="synthetic-owner"');
    expect(html).not.toContain('action="/api');
    expect(html).not.toContain('method="post"');
  });
});
