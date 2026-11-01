import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { DeviceDetailTabs } from './device-detail-tabs';
import { ConnectivityBreakdown, InventoryFreshness, UnconnectedMetrics } from './health-sections';

describe('NXT-02 read-only monitoring and detail presentation', () => {
  it('renders all metrics as unavailable rather than as fabricated zeros', () => {
    const html = renderToStaticMarkup(<UnconnectedMetrics />);
    expect(html).toContain('在线设备');
    expect(html).toContain('已授权设备总数');
    expect(html).toContain('无已验证的心跳汇总');
    expect(html).not.toContain('0 台');
    expect(html).not.toContain('演示设备 A');
  });
  it('keeps server connectivity, inventory freshness and queue coverage distinct', () => {
    const connection = renderToStaticMarkup(<ConnectivityBreakdown />);
    const inventory = renderToStaticMarkup(<InventoryFreshness />);
    expect(connection).toContain('设备连接分布');
    expect(connection).toContain('暂无连接状态统计数据');
    expect(connection).toContain('不计算健康百分比');
    expect(inventory).toContain('最新库存快照');
    expect(inventory).toContain('心跳接收状态');
    expect(inventory).toContain('待发送队列统计');
  });
  it('provides accessible read-only hardware/network/health tabs', () => {
    const html = renderToStaticMarkup(<DeviceDetailTabs />);
    expect(html).toContain('role="tablist"');
    expect(html).toContain('role="tab"');
    expect(html).toContain('aria-selected="true"');
    expect(html).toContain('role="tabpanel"');
    expect(html).toContain('硬件资产未连接');
    expect(html).toContain('网络');
    expect(html).toContain('健康');
    expect(html).not.toContain('Intel Core');
  });
});
