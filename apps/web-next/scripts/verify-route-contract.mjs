import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { verifyRuntimeIsolation } from './verify-runtime-isolation.mjs';

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
  'src/app/synthetic-console-candidate', 'src/app/synthetic-console-route']) {
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
      if (/staged-console-read|staged-console-auth|staged-console-workspace|\.generated-contract/.test(source)) {
        throw new Error('NXT-03 pending acceptance: runtime imported staged transport/model: ' + file);
      }
    }
  }
}
for (const folder of ['src/app', 'src/components']) inspectRuntimeSource(join(process.cwd(),folder));
// NXT-16: semantic palette has a SINGLE authoritative source. Avoid the
// three-layer :root drift that previously changed colors in import order.
const canonicalStyle = readFileSync('src/app/lanveriq-bc.css', 'utf8');
const legacyStyles = [
  readFileSync('src/app/globals.css', 'utf8'),
  readFileSync('src/app/craftwork-theme.css', 'utf8'),
].join('\n');
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
console.log('NXT-16 single-source semantic design tokens and compact asset layout passed');

const verified = verifyRuntimeIsolation(process.cwd());
console.log('NXT-06 staged runtime dependency graph isolated:',
  verified.checkedEntries, 'entry files /', verified.checkedModules, 'reachable modules');
console.log('NXT-03 staged transport stays unmounted; no business proxy, credentials or live API use');
