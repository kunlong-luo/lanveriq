import { existsSync, readFileSync } from 'node:fs';
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
console.log('NXT-01 preview route and no-business-proxy source gate passed');
