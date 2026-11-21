import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { verifyRuntimeIsolation } from './verify-runtime-isolation.mjs';
import { verifyStyleStructure } from './verify-css-structure.mjs';

// Source-only CI guard; not a browser TLS/PG18 acceptance.
const required = [
  'src/app/layout.tsx', 'src/app/(console)/layout.tsx',
  'src/app/(console)/overview/page.tsx', 'src/app/(console)/monitoring/page.tsx',
  'src/app/(console)/devices/page.tsx', 'src/app/(console)/devices/[id]/page.tsx',
  'src/app/(console)/enrollment/page.tsx', 'src/app/(console)/settings/page.tsx',
  'src/app/(console)/alerts/page.tsx', 'src/app/(console)/policies/page.tsx',
  'src/app/(console)/reports/page.tsx', 'src/app/(console)/design-system/page.tsx',
  'src/app/lanveriq-bc.css', 'src/components/planned-console-page.tsx',
  'src/app/(auth)/login/page.tsx', 'src/components/login-preview.tsx',
];
for (const pathname of required) {
  if (!existsSync(join(process.cwd(), pathname))) {
    throw new Error('NXT-01 missing intended route: ' + pathname);
  }
}
for (const forbidden of ['src/app/api', 'src/proxy.ts', 'src/middleware.ts',
  'src/app/(console)/api', 'src/app/(auth)/api',
  // The credentialed test page is generated only inside manual CI; it must
  // never be committed or built in the ordinary disabled-login preview.
  'src/app/synthetic-auth-probe', 'src/app/synthetic-multi-admin-probe',
  'src/app/synthetic-console-candidate', 'src/app/synthetic-console-route',
  // NXT-17: CI fixture is generated only after ordinary source passed this gate.
  'src/app/synthetic-directory-probe']) {
  if (existsSync(join(process.cwd(), forbidden))) {
    throw new Error('NXT-01 cannot enable a business/API proxy: ' + forbidden);
  }
}
const config = readFileSync('next.config.ts', 'utf8');
if (/rewrites\s*[:(]|redirects\s*[:(]/.test(config)) {
  throw new Error('NXT-01 does not allow Next.js proxy rewrites');
}
const login = readFileSync('src/components/login-preview.tsx', 'utf8');
if (/fetch\s*\(|method\s*=\s*['"]post|<form\b/i.test(login)) {
  throw new Error('NXT-01 login must not accept or submit credentials');
}
// NXT-03 transport/type bridge exists only in a deliberately non-mounted
// library until manual database/TLS/browser acceptance and a reviewed
// runtime enablement commit. Even a correct fetch implementation must not
// become reachable by casually importing it into a page or a client shell.
function inspectRuntimeSource(folder) {
  for (const entry of readdirSync(folder, { withFileTypes: true })) {
    const file = join(folder, entry.name);
    if (entry.isDirectory()) inspectRuntimeSource(file);
    else if (/\.[jt]sx?$/.test(entry.name)) {
      const source = readFileSync(file, 'utf8');
      if (/staged-console-read|staged-console-auth|staged-console-workspace|synthetic-only-directory-probe|\.generated-contract/.test(source)) {
        throw new Error('NXT-03 pending acceptance: runtime imported staged transport/model: ' + file);
      }
    }
  }
}
for (const folder of ['src/app', 'src/components']) inspectRuntimeSource(join(process.cwd(),folder));
// NXT-17: one consolidated brand stylesheet after Tailwind baseline.
// Reject an accidental second theme import, recreated legacy file or drift.
const appLayout = readFileSync('src/app/layout.tsx', 'utf8');
if (existsSync('src/app/craftwork-theme.css') ||
    appLayout.includes('craftwork-theme.css') ||
    !appLayout.includes("import './globals.css';") ||
    !appLayout.includes("import './lanveriq-bc.css';")) {
  throw new Error('NXT-17 refuses overlapping B+C theme imports and legacy stylesheets');
}
const canonicalStyle = readFileSync('src/app/lanveriq-bc.css', 'utf8');
const legacyStyles = readFileSync('src/app/globals.css', 'utf8');
if (!canonicalStyle.includes('NXT-17 / CONSOLIDATED LANVERIQ THEME') ||
    !canonicalStyle.includes('NXT-13 / LANVERIQ VISUAL SYSTEM') ||
    !canonicalStyle.includes('NXT-14 / B+C fusion implementation')) {
  throw new Error('NXT-17 must retain the original CSS cascade order after merging');
}
const semanticTokens = [
  '--background', '--foreground', '--card', '--card-foreground', '--border',
  '--muted-foreground', '--lan-ink', '--lan-violet', '--lan-lime',
  '--lan-stroke', '--lan-ease', '--bc-blue', '--bc-violet', '--bc-radius',
];
function tokenDeclarations(css, token) {
  // CSS declarations are newline-delimited in the reviewed theme. This does
  // not match a var(--token) reference or a Tailwind --color-token alias.
  return css.split('\n').filter(line =>
    new RegExp('^\\s*' + token + '\\s*:').test(line)).length;
}
for (const token of semanticTokens) {
  if (tokenDeclarations(canonicalStyle, token) !== 1 ||
      tokenDeclarations(legacyStyles, token) !== 0) {
    throw new Error('NXT-16 duplicate or missing canonical semantic token: ' + token);
  }
}
if (!canonicalStyle.includes('.bc-assets-workspace-head') ||
    canonicalStyle.includes('.bc-assets-art-square')) {
  throw new Error('NXT-16 asset workspace must stay operational-first and compact');
}
verifyStyleStructure(canonicalStyle);
console.log('NXT-18 CSS audit passed: zero duplicate selectors in the same responsive scope');
console.log('NXT-17 consolidated theme, single-source tokens and compact asset layout passed');

const verified = verifyRuntimeIsolation(process.cwd());
console.log('NXT-06 staged runtime dependency graph isolated:',
  verified.checkedEntries, 'entry files /', verified.checkedModules, 'reachable modules');
console.log('NXT-03 staged transport stays unmounted; no business proxy, credentials or live API use');
