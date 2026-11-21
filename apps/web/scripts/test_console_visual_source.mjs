import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

test('Next.js 16 App Router is the canonical operations UI with the final theme', () => {
  const packageJson = JSON.parse(read('package.json'));
  assert.match(packageJson.scripts.build, /^next build$/);
  assert.equal(packageJson.dependencies.next, '16.4.5');
  const layout = read('src/app/layout.tsx');
  assert.match(layout, /import '\.\/globals\.css';/);
  assert.match(layout, /import '\.\/lanveriq-bc\.css';/);
  assert.match(layout, /import '\.\/console-polish\.css';/);
  assert.ok(layout.indexOf('console-polish.css') > layout.indexOf('lanveriq-bc.css'));
});

test('operational overview removes illustration, retains real disconnected boundary', () => {
  const overview = read('src/app/(console)/overview/page.tsx');
  assert.doesNotMatch(overview, /hero-artwork|bc-illustration-laptop|illustration-server/);
  assert.match(overview, /className="lv-hero-guardrails"/);
  assert.match(overview, /<strong>远程连接<\/strong> 未启用/);
  assert.match(overview, /<strong>设备采集<\/strong> 未启用/);
  assert.match(overview, /<UnconnectedMetrics\s*\/>/);
  assert.match(overview, /<DeviceDirectory state=\{\{ kind: 'unconnected' \}\}\s*\/>/);
  assert.doesNotMatch(overview, /演示设备|Math\.random|伪造统计/);
});

test('visual refinement stays a CSS-only accessible layer', () => {
  const theme = read('src/app/console-polish.css');
  assert.match(theme, /--background:\s*#f5f7fa/i);
  assert.match(theme, /--lan-violet:\s*#4d5fc9/i);
  assert.match(theme, /\.bc-overview \.overview-hero/);
  assert.match(theme, /--lan-editorial-surface:\s*#fff/i);
  assert.match(theme, /background:\s*radial-gradient\(circle at 88% 19%/);
  assert.match(theme, /\.bc-console \.nav-item\.selected/);
  assert.match(theme, /\.lv-hero-guardrails/);
  assert.match(theme, /@media\s*\(max-width:\s*750px\)/);
  assert.match(theme, /prefers-reduced-motion/);
  assert.doesNotMatch(theme, /html\s*,\s*body\s*\{[^}]*overflow:\s*hidden/i);
  const design = read('src/app/(console)/design-system/page.tsx');
  for (const token of ['#4D5FC9','#4669D1','#CEF4E5','#172235','#F5F7FA','#E4E9F0'])
    assert.ok(design.includes(token), 'design-system must reflect '+token);
  assert.ok(!design.includes('#6A5BFF'), 'do not advertise abandoned purple palette');
});

test('local route palette is wired to the visible search control, never to device APIs', () => {
  const shell = read('src/components/console-shell.tsx');
  const palette = read('src/components/console-command-palette.tsx');
  const nav = read('src/lib/console-navigation.ts');
  assert.match(shell, /<ConsoleCommandPalette \/>/);
  assert.match(palette, /<Dialog\.Popup className="bc-command-dialog"/);
  assert.match(palette, /event\.key\.toLowerCase\(\) !== 'k'/);
  assert.match(palette, /findConsolePages\(query\)/);
  assert.match(nav, /consoleRoutes\.filter\(/);
  assert.doesNotMatch(palette, /fetch\(|axios|WebSocket|localStorage|sessionStorage|staged-console/);
  assert.doesNotMatch(shell, /全局搜索尚未开放/);
  const styles = read('src/app/console-polish.css');
  assert.match(styles, /\.bc-command-dialog\s*\{/);
  assert.match(styles, /\.bc-command-input-row:focus-within/);
});

test('NXT-26 directory controls use accessible Base UI Select, no native dropdown', () => {
  const directory = read('src/components/device-directory.tsx');
  const select = read('src/components/ui/console-select.tsx');
  const style = read('src/app/console-polish.css');
  const chromium = read('scripts/verify-directory-fixture-chromium.py');
  assert.match(directory, /import \{ ConsoleSelect \} from '\.\/ui\/console-select'/);
  assert.match(directory, /<ConsoleSelect label="筛选设备组"/);
  assert.match(directory, /<ConsoleSelect label="排序当前授权页"/);
  assert.doesNotMatch(directory, /NativeSelect|<select|<option/);
  assert.match(select, /@base-ui\/react\/select/);
  assert.match(select, /<Select\.Trigger/);
  assert.match(select, /<Select\.Popup/);
  assert.match(select, /<Select\.Item/);
  assert.match(select, /disabled=\{disabled\}/);
  assert.doesNotMatch(select, /fetch\(|localStorage|WebSocket|staged-console|axios/);
  assert.match(style, /\.lv-select-trigger/);
  assert.match(style, /\.lv-select-popup/);
  assert.match(style, /@media \(max-width: 750px\)/);
  assert.match(chromium, /get_by_role\("option"/);
  assert.doesNotMatch(chromium, /\.select_option\(|native-select/);
});

test('NXT-27 shared evidence states are static, scoped and consistent across pages', () => {
  const state = read('src/components/ui/evidence-state.tsx');
  const monitoring = read('src/components/health-sections.tsx');
  const detailTabs = read('src/components/device-detail-tabs.tsx');
  const detailPage = read('src/app/(console)/devices/[id]/page.tsx');
  const chromium = read('scripts/verify-preview-chromium.py');
  const css = read('src/app/console-polish.css');
  const legacyCss = read('src/app/lanveriq-bc.css');
  for (const token of ['unconnected', 'restricted', 'unverified', 'role="note"']) {
    assert.ok(state.includes(token), 'evidence-state missing '+token);
  }
  assert.doesNotMatch(state, /fetch\(|axios|WebSocket|localStorage|sessionStorage|staged-console|useEffect|useState|navigator\./);
  assert.match(monitoring, /import \{ EvidenceNotice, EvidenceValue \}/);
  assert.match(monitoring, /<EvidenceNotice status="unconnected" variant="chart"/);
  assert.match(detailTabs, /status: 'restricted'/);
  assert.match(detailTabs, /status: 'unverified'/);
  assert.match(detailTabs, /<EvidenceNotice status=\{detail.status\}/);
  assert.match(detailPage, /<EvidenceValue context=\{label\}/);
  assert.doesNotMatch(detailPage, /fetch\(|WebSocket|cookies\(|headers\(/);
  assert.match(css, /\.lv-evidence-notice--restricted/);
  assert.match(css, /\.lv-evidence-value/);
  assert.doesNotMatch(legacyCss, /\.bc-chart-message/);
  assert.match(chromium, /nxt27_health_must_not_infer_online_or_offline/);
  assert.match(chromium, /nxt27_monitoring_unexpected_network/);
});

test('NXT-28 compact navigation scrolls without native bars and uses one visual theme', () => {
  const css = read('src/app/console-polish.css');
  const chromium = read('scripts/verify-preview-chromium.py');
  assert.match(css, /NXT-28/);
  assert.match(css, /\.bc-console \.sidebar\s*\{\s*overflow-y: auto;/);
  assert.match(css, /\.bc-console \.sidebar::-webkit-scrollbar\s*\{ display: none;/);
  assert.match(css, /\.bc-mobile-sheet\s*\{\s*background: #fff;/);
  assert.match(css, /\.bc-mobile-sheet \.nav-item\.selected\s*\{\s*color: #fff;\s*background: #172235;/);
  assert.match(css, /\.bc-mobile-sheet::-webkit-scrollbar\s*\{ display: none;/);
  assert.match(chromium, /def check_compact_navigation\(browser\):/);
  assert.match(chromium, /check_compact_navigation\(browser\)/);
  assert.match(chromium, /nxt28_short_desktop_sidebar_must_scroll/);
  assert.match(chromium, /nxt28_short_mobile_drawer_must_scroll/);
  assert.match(chromium, /nxt28_short_mobile_escape_focus_not_restored/);
  assert.doesNotMatch(css, /\.bc-console \.sidebar\s*\{[^}]*overflow-y:\s*hidden/i);
});
