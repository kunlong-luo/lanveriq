import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { basename, resolve, join } from 'node:path';

// This writes a temporary Next.js route ONLY inside a disposable GitHub
// runner explicitly opted into all three synthetic gates. Normal builds,
// deployed images, Vite preview and committed App Router pages never mount it.
const gates = [
  'LANVERIQ_SYNTHETIC_BROWSER_PROBE',
  'LANVERIQ_SYNTHETIC_NEXTJS_PROBE',
  'LANVERIQ_SYNTHETIC_AUTH_UI_PROBE',
];
if (process.env.GITHUB_ACTIONS !== 'true' ||
    gates.some(key => process.env[key] !== '1') ||
    basename(process.cwd()) !== 'web-next') {
  throw new Error('Refusing synthetic auth fixture outside explicit isolated CI gates');
}
const multiAdmin = process.env.LANVERIQ_SYNTHETIC_MULTI_ADMIN_PROBE ?? '0';
if (!['0', '1'].includes(multiAdmin)) {
  throw new Error('Unexpected multi-admin fixture gate');
}
const root = resolve(process.cwd());
if (!existsSync(join(root, 'src/synthetic-only-auth-probe.tsx')) ||
    !existsSync(join(root, 'src/app/(auth)/login/page.tsx'))) {
  throw new Error('Synthetic auth fixture requires unchanged Next.js preview');
}
const dir = join(root, 'src/app/synthetic-auth-probe');
const target = join(dir, 'page.tsx');
const multiDir = join(root, 'src/app/synthetic-multi-admin-probe');
const candidateDir = join(root, 'src/app/synthetic-console-candidate');
const multiRouteDir = join(root, 'src/app/synthetic-console-route');
if (existsSync(dir) || existsSync(target) || existsSync(multiDir) ||
    existsSync(candidateDir) || existsSync(multiRouteDir) ||
    (multiAdmin === '1' &&
     (!existsSync(join(root, 'src/synthetic-only-multi-admin-probe.tsx')) ||
      !existsSync(join(root, 'src/staged-console-workspace.tsx')) ||
      !existsSync(join(root, 'src/staged-console-route-boundary.tsx'))))) {
  throw new Error('Synthetic auth routes already exist or staged source fixture missing');
}
mkdirSync(dir, { recursive: false, mode: 0o700 });
writeFileSync(target, `import { SyntheticOnlyAuthProbe } from '@/synthetic-only-auth-probe';

export const dynamic = 'force-dynamic';
export default function SyntheticAuthPage() {
  return <SyntheticOnlyAuthProbe />;
}
`, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
console.log('Synthetic browser auth fixture staged in disposable runner only');

if (multiAdmin === '1') {
  mkdirSync(multiDir, { recursive: false, mode: 0o700 });
  writeFileSync(join(multiDir, 'page.tsx'), `import { SyntheticMultiAdminProbe } from '@/synthetic-only-multi-admin-probe';

export const dynamic = 'force-dynamic';
export default function SyntheticMultiAdminPage() {
  return <SyntheticMultiAdminProbe />;
}
`, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
  console.log('Synthetic multi-admin browser fixture staged in disposable runner only');
}

if (multiAdmin === '1') {
  // Product-shaped UI candidate is NEVER mounted in a committed App Router
  // route. The fourth synthetic gate is the only way to compile/run it.
  mkdirSync(candidateDir, { recursive: false, mode: 0o700 });
  writeFileSync(join(candidateDir, 'page.tsx'), `import { StagedConsoleWorkspace } from '@/staged-console-workspace';

export const dynamic = 'force-dynamic';
export default function SyntheticCandidatePage() {
  return <StagedConsoleWorkspace />;
}
`, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
  console.log('Staged product-shaped Console candidate mounted in disposable runner only');
}

if (multiAdmin === '1') {
  // Three ACTUAL App Router URLs, not React tabs; full-document navigation
  // triggers a fresh synthetic Spring Session check in each isolated page.
  mkdirSync(multiRouteDir, { recursive: false, mode: 0o700 });
  for (const view of ['overview', 'devices', 'monitoring']) {
    const pageDir = join(multiRouteDir, view);
    mkdirSync(pageDir, { recursive: false, mode: 0o700 });
    writeFileSync(join(pageDir, 'page.tsx'), `import { StagedConsoleRouteBoundary } from '@/staged-console-route-boundary';

export const dynamic = 'force-dynamic';
export default function SyntheticMultiRoutePage() {
  return <StagedConsoleRouteBoundary view="${view}" />;
}
`, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
  }
  console.log('Three gated synthetic multi-route session pages mounted in disposable runner only');
}
