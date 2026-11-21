import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ConsoleSelect } from './console-select';

/**
 * NXT-26: the real Chromium fixture separately checks keyboard, popup,
 * selection, auth-page replacement and revocation. SSR tests guard the
 * disconnected and accessible HTML defaults without mounting business APIs.
 */
const options = [
  { value: '', label: '全部授权设备组' },
  { value: 'CI测试组甲', label: 'CI测试组甲' },
] as const;

describe('Base UI console select', () => {
  it('uses semantic Base UI combobox instead of a native select in disabled preview', () => {
    const html = renderToStaticMarkup(
      <ConsoleSelect label="筛选设备组" value="" options={options}
        disabled onChange={vi.fn()} />,
    );
    expect(html).toContain('role="combobox"');
    expect(html).toContain('aria-label="筛选设备组"');
    expect(html).toContain('disabled');
    expect(html).toContain('全部授权设备组');
    expect(html).not.toContain('<select');
    expect(html).not.toContain('data-slot="native-select"');
    expect(html).not.toContain('CI合成设备');
  });

  it('renders a controlled authorized-page selection without fetching any data', () => {
    const html = renderToStaticMarkup(
      <ConsoleSelect label="筛选设备组" value="CI测试组甲"
        options={options} disabled={false} onChange={vi.fn()} />,
    );
    expect(html).toContain('role="combobox"');
    expect(html).toContain('CI测试组甲');
    expect(html).not.toContain('disabled=""');
    expect(html).not.toContain('<select');
  });
});
