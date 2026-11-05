import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StagedConsoleRouteBoundary, type SyntheticRouteView } from './staged-console-route-boundary';

describe('NXT-08-A isolated App Router multi-page session boundary', () => {
  it.each(['overview', 'devices', 'monitoring'] as const)(
    'never SSRs credentials, authorized navigation, metrics or device rows on %s', view => {
      const html = renderToStaticMarkup(<StagedConsoleRouteBoundary view={view}/>);
      expect(html).toContain('data-route-view="' + view + '"');
      expect(html).toContain('data-route-state="checking"');
      expect(html).toContain('正在重新验证合成管理员会话');
      expect(html).not.toContain('data-route-principal="synthetic-browser-a"');
      expect(html).not.toContain('SyntheticBrowserA-1');
      expect(html).not.toContain('已授权设备总数');
      expect(html).not.toContain('href="/synthetic-console-route/');
      expect(html).not.toContain('id="route-pass"');
      expect(html).not.toContain('role="tablist"');
      expect(html).not.toContain('action="/api');
      expect(html).not.toContain('sessionStorage');
      expect(html).not.toContain('localStorage');
    },
  );

  it('keeps each route deterministic and excludes account information from markup', () => {
    for (const view of ['overview', 'devices', 'monitoring'] as SyntheticRouteView[]) {
      const html = renderToStaticMarkup(<StagedConsoleRouteBoundary view={view}/>);
      expect(html).toContain('data-route-view="' + view + '"');
      expect(html).toContain('data-route-principal=""');
      expect(html).not.toContain('/api/v1/');
    }
  });
});
