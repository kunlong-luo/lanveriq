import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { EvidenceNotice, EvidenceValue, type EvidenceStatus } from './evidence-state';

const statuses: readonly EvidenceStatus[] = ['unconnected', 'restricted', 'unverified'];

describe('NXT-27 evidence boundary primitives (no live data)', () => {
  it.each(statuses)('renders %s as a readable static note with no network or controls', status => {
    const html = renderToStaticMarkup(<EvidenceNotice status={status}
      title="审查中的状态" description="当前尚无受信任证据" />);
    expect(html).toContain(`data-evidence-status="${status}"`);
    expect(html).toContain('role="note"');
    expect(html).toContain('审查中的状态');
    expect(html).toContain('当前尚无受信任证据');
    expect(html).toContain('aria-hidden="true"');
    expect(html).not.toContain('href=');
    expect(html).not.toContain('<button');
    expect(html).not.toContain('正在在线监控');
  });

  it('uses a consistent accessible, factual unknown value rather than zero', () => {
    const missing = renderToStaticMarkup(<EvidenceValue context="最后心跳" />);
    expect(missing).toContain('最后心跳：未连接');
    expect(missing).toContain('data-evidence-status="unconnected"');
    expect(missing).not.toContain('0 台');
    expect(missing).not.toContain('在线');
    const restricted = renderToStaticMarkup(<EvidenceValue status="restricted" context="IP" />);
    expect(restricted).toContain('IP：权限受限');
    const unverified = renderToStaticMarkup(<EvidenceValue status="unverified" context="心跳" />);
    expect(unverified).toContain('心跳：尚未核验');
  });

  it('provides chart and detail variants without inventing a trend', () => {
    const chart = renderToStaticMarkup(<EvidenceNotice status="unconnected" variant="chart"
      title="未连接数据源" description="不能绘制趋势" />);
    const detail = renderToStaticMarkup(<EvidenceNotice status="unverified" variant="detail"
      title="尚无可信健康证据" description="不能推断在线或离线" />);
    expect(chart).toContain('lv-evidence-notice--chart');
    expect(detail).toContain('lv-evidence-notice--detail');
    expect(detail).toContain('lv-evidence-notice--unverified');
    expect(detail).not.toContain('0%');
  });
});
