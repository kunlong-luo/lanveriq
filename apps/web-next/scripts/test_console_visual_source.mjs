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
