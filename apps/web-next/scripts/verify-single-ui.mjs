import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * NXT-23: repository-level canonical UI safety boundary.
 * Keep exactly one runnable frontend. Historical Vite sources remain only
 * in Git history; never permit a second dev server / 5173 runbook to return.
 * This verifier does not execute applications or contact a server.
 */
export function verifySingleUi(root) {
  if (existsSync(join(root, 'apps/web'))) {
    throw new Error('Legacy apps/web exists: only apps/web-next may be runnable');
  }
  if (existsSync(join(root, '.github/workflows/web-unit.yml'))) {
    throw new Error('Legacy Vite CI exists: remove web-unit.yml');
  }
  const manifestPath = join(root, 'apps/web-next/package.json');
  if (!existsSync(manifestPath)) throw new Error('Canonical Next.js manifest missing');
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  assert.equal(manifest.name, '@lanveriq/web-next');
  assert.match(manifest.scripts?.dev ?? '', /^next dev --hostname 127\.0\.0\.1 --port 3100$/);
  assert.match(manifest.scripts?.build ?? '', /^next build$/);
  assert.ok(manifest.dependencies?.next?.startsWith('16.'), 'canonical Next.js dependency missing');
  for (const name of ['README.md', 'README.zh.md']) {
    const doc = readFileSync(join(root, name), 'utf8');
    if (/(?:^|\n)\s*cd\s+apps\/web\s*(?:\n|$)/m.test(doc) ||
        /https?:\/\/127\.0\.0\.1:5173\b/.test(doc) ||
        /\]\(apps\/web\/README\.md\)/.test(doc)) {
      throw new Error(name + ': active legacy UI launch instruction or link found');
    }
    if (!doc.includes('apps/web-next') || !doc.includes('3100')) {
      throw new Error(name + ': canonical UI launch instructions missing');
    }
  }
  return true;
}

const here = fileURLToPath(import.meta.url);
if (process.argv[1] && resolve(process.argv[1]) === here) {
  const root = resolve(dirname(here), '../../..');
  verifySingleUi(root);
  console.log('PASS: one Next.js 16 console, port 3100; legacy Vite code and runner absent');
}
