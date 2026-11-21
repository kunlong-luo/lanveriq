import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * NXT-24: apps/web is now the only Next.js UI, not the retired Vite project.
 * Refuse obsolete web-next directory, Vite reintroduction, wrong manifest
 * or legacy startup commands without starting any app or contacting an API.
 */
export function verifySingleUi(root) {
  if (existsSync(join(root, 'apps/web-next'))) {
    throw new Error('Obsolete apps/web-next directory: canonical Next.js is apps/web');
  }
  if (existsSync(join(root, '.github/workflows/web-next-unit.yml'))) {
    throw new Error('Obsolete web-next-unit workflow remains');
  }
  if (existsSync(join(root, 'apps/web/vite.config.ts')) ||
      existsSync(join(root, 'apps/web/vite.config.mts')) ||
      existsSync(join(root, 'apps/web/vite.config.js'))) {
    throw new Error('Legacy Vite config must not return to apps/web');
  }
  const manifestPath = join(root, 'apps/web/package.json');
  if (!existsSync(manifestPath)) throw new Error('Canonical Next.js manifest missing at apps/web');
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  assert.equal(manifest.name, '@lanveriq/web');
  assert.match(manifest.scripts?.dev ?? '', /^next dev --hostname 127\.0\.0\.1 --port 3100$/);
  assert.match(manifest.scripts?.build ?? '', /^next build$/);
  assert.ok(manifest.dependencies?.next?.startsWith('16.'), 'canonical Next.js dependency missing');
  assert.ok(!Object.keys({ ...manifest.dependencies, ...manifest.devDependencies }).some(
    name => name === 'vite' || name.startsWith('@vitejs/')),
    'Vite dependencies forbidden in canonical frontend');
  const workflowPath = join(root, '.github/workflows/web-unit.yml');
  if (!existsSync(workflowPath)) throw new Error('Canonical Next.js web-unit workflow missing');
  const workflow = readFileSync(workflowPath, 'utf8');
  if (!workflow.includes('name: Next.js fast source and preview checks') ||
      !workflow.includes("'apps/web/**'") ||
      workflow.includes('apps/web-next')) {
    throw new Error('web-unit workflow must test canonical apps/web Next.js');
  }
  for (const name of ['README.md', 'README.zh.md']) {
    const doc = readFileSync(join(root, name), 'utf8');
    if (/(?:^|\n)\s*cd\s+apps\/web-next\s*(?:\n|$)/m.test(doc) ||
        /https?:\/\/127\.0\.0\.1:5173\b/.test(doc) ||
        /\]\(apps\/web-next\/README\.md\)/.test(doc)) {
      throw new Error(name + ': obsolete web-next launch/link or Vite port present');
    }
    if (!doc.includes('apps/web') || !doc.includes('3100')) {
      throw new Error(name + ': canonical apps/web Next.js launch instructions missing');
    }
  }
  return true;
}

const here = fileURLToPath(import.meta.url);
if (process.argv[1] && resolve(process.argv[1]) === here) {
  const root = resolve(dirname(here), '../../..');
  verifySingleUi(root);
  console.log('PASS: apps/web is the sole Next.js console on 3100; Vite and web-next absent');
}
