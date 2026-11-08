import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';

/**
 * NXT-17 standalone DeviceDirectory test route. Do NOT install it on a laptop,
 * production server, Pages build or ordinary PR build. GitHub Actions runner
 * explicitly opts in after its disconnected production preview was accepted.
 */
if (process.env.GITHUB_ACTIONS !== 'true' ||
    process.env.LANVERIQ_NXT17_BROWSER_FIXTURE !== '1' ||
    basename(process.cwd()) !== 'web-next') {
  throw new Error('NXT-17 refuses to mount synthetic directory outside isolated CI');
}
const root = resolve(process.cwd());
if (!existsSync(join(root, 'src/synthetic-only-directory-probe.tsx')) ||
    !existsSync(join(root, 'src/app/(console)/devices/page.tsx'))) {
  throw new Error('Missing unchanged canonical directory or unmounted fixture');
}
const directory = join(root, 'src/app/synthetic-directory-probe');
if (existsSync(directory)) {
  throw new Error('Synthetic directory route already exists in source');
}
mkdirSync(directory, { recursive:false, mode:0o700 });
writeFileSync(join(directory, 'page.tsx'),
  `import { SyntheticDirectoryProbe } from '@/synthetic-only-directory-probe';

export default function SyntheticDirectoryAcceptancePage() {
  return <SyntheticDirectoryProbe />;
}
`, { encoding:'utf8', flag:'wx', mode:0o600 });
console.log('NXT-17 CI-only disposable directory route mounted; no live services.');
