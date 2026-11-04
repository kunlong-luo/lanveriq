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
const root = resolve(process.cwd());
if (!existsSync(join(root, 'src/synthetic-only-auth-probe.tsx')) ||
    !existsSync(join(root, 'src/app/(auth)/login/page.tsx'))) {
  throw new Error('Synthetic auth fixture requires unchanged Next.js preview');
}
const dir = join(root, 'src/app/__synthetic_auth_probe');
const target = join(dir, 'page.tsx');
if (existsSync(dir) || existsSync(target)) {
  throw new Error('Synthetic auth route already exists; refusing overwrite');
}
mkdirSync(dir, { recursive: false, mode: 0o700 });
writeFileSync(target, `import { SyntheticOnlyAuthProbe } from '@/synthetic-only-auth-probe';

export const dynamic = 'force-dynamic';
export default function SyntheticAuthPage() {
  return <SyntheticOnlyAuthProbe />;
}
`, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
console.log('Synthetic browser auth fixture staged in disposable runner only');
