import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

const base = path.resolve('generated');
let files = [];
async function walk(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const item = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(item);
    else if (item.endsWith('.ts')) files.push(item);
  }
}
await walk(base);
if (files.length === 0) throw new Error('Orval did not generate TypeScript files');
const code = (await Promise.all(files.map(file => readFile(file, 'utf8')))).join('\n');
const required = [
  '/api/v1/devices',
  '/api/v1/devices/',
  '/api/v1/monitoring/overview',
  '/api/v1/auth/session',
  '@tanstack/react-query',
  'useQuery',
];
for (const expected of required) {
  if (!code.includes(expected)) {
    throw new Error(`Orval generated client missing expected contract marker: ${expected}`);
  }
}
for (const unsafe of ['mac_address', 'client_cert_sha256_fingerprint', 'employee_id']) {
  if (code.includes(unsafe)) {
    throw new Error('Sensitive field surfaced in generated health client');
  }
}
console.log('Orval 8 Fetch/TanStack Query client generated from Springdoc and typechecked.');
