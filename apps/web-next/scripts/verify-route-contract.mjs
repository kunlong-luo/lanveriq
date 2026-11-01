import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

// Source-only CI guard; not a browser TLS/PG18 acceptance.
const required = [
  'src/app/layout.tsx', 'src/app/(console)/layout.tsx',
  'src/app/(console)/overview/page.tsx', 'src/app/(console)/monitoring/page.tsx',
  'src/app/(console)/devices/page.tsx', 'src/app/(console)/devices/[id]/page.tsx',
  'src/app/(console)/enrollment/page.tsx', 'src/app/(console)/settings/page.tsx',
  'src/app/(auth)/login/page.tsx', 'src/components/login-preview.tsx',
];
for (const pathname of required) {
  if (!existsSync(join(process.cwd(), pathname))) {
    throw new Error('NXT-01 missing intended route: ' + pathname);
  }
}
for (const forbidden of ['src/app/api', 'src/proxy.ts', 'src/middleware.ts',
  'src/app/(console)/api', 'src/app/(auth)/api']) {
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
      if (/staged-console-read|\.generated-contract/.test(source)) {
        throw new Error('NXT-03 pending acceptance: runtime imported staged transport/model: ' + file);
      }
    }
  }
}
for (const folder of ['src/app', 'src/components']) inspectRuntimeSource(join(process.cwd(),folder));
console.log('NXT-03 staged transport stays unmounted; no business proxy, credentials or live API use');
