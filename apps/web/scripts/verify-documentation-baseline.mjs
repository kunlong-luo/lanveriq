import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * NXT-25: lightweight source-only check of CURRENT documentation contracts.
 * Historical Vite/NXT-01 to NXT-23 evidence may still mention the old paths;
 * only authoritative active sections below are guarded.
 * No external network, credentials, browser or device accesses.
 */
export const documentationRequirements = [
  {
    path: 'docs/development/standards.md',
    required: ['apps/web/', 'Next.js 16.4.5', 'NXT-24', '旧 Vite'],
    forbidden: [/^- `apps\/web\/`：历史 React\/Vite 候选/m],
  },
  {
    path: 'docs/development/technology.md',
    required: ['| Web 控制台 |', '`apps/web` 是唯一 Next.js 主前端', 'NXT-24'],
    forbidden: [/\| Web 控制台 \|[^\n]*`apps\/web` Vite/],
  },
  {
    path: 'docs/development/frontend-design-system.md',
    required: ['2026-11-21 唯一现行 UI 规范', 'console-polish.css', '#F5F7FA', '#172235', '#4D5FC9', 'Base UI React 1.9.0'],
    forbidden: [/^\| v0\.1 \| React\/Vite/m],
  },
  {
    path: 'docs/development/nextjs-single-ui-consolidation-2026-11-21.md',
    required: ['NXT-23 / NXT-24', 'NXT-24', 'web-unit.yml', 'verify-single-ui.mjs'],
    forbidden: [/^1\. Reintroduction of `apps\/web` as a second executable frontend\./m],
  },
  {
    path: 'docs/architecture/adrs/0008-nextjs-app-router-console.md',
    required: ['Current interpretation (NXT-24', 'apps/web', 'NXT-23'],
    forbidden: [],
  },
  {
    path: 'docs/testing/nextjs-nxt02-manual.md',
    required: ['Current NXT-24 browser status', '44876019297'],
    forbidden: [/\| Regression \| Original apps\/web preview \| Old Vite routes still build/],
  },
  {
    path: 'docs/operations/release.md',
    required: ['2026-11-21 UI-only acceptance update', 'apps/web'],
    forbidden: [],
  },
  {
    path: 'docs/README.md',
    required: ['2026-11-21 current Web baseline', 'nextjs-path-rename-nxt24-2026-11-21.md'],
    forbidden: [],
  },
];

export function verifyDocumentationBaseline(root) {
  for (const { path, required, forbidden } of documentationRequirements) {
    let contents;
    try { contents = readFileSync(join(root, path), 'utf8'); }
    catch { throw new Error('Current documentation missing: ' + path); }
    for (const item of required) {
      if (!contents.includes(item)) {
        throw new Error('Current documentation drift: ' + path + ' missing ' + item);
      }
    }
    for (const pattern of forbidden) {
      if (pattern.test(contents)) {
        throw new Error('Obsolete active guidance reintroduced: ' + path);
      }
    }
  }
  return true;
}

const here = fileURLToPath(import.meta.url);
if (process.argv[1] && resolve(process.argv[1]) === here) {
  verifyDocumentationBaseline(resolve(dirname(here), '../../..'));
  console.log('PASS: Next.js apps/web canonical docs, token source and CI claims stay aligned');
}
