import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve, sep } from 'node:path';
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
    required: ['2026-11-21 现行发布边界', 'apps/web', 'CURRENT_STATUS.md'],
    forbidden: [],
  },
  {
    path: 'docs/README.md',
    required: ['2026-11-21 current Web baseline', 'nextjs-path-rename-nxt24-2026-11-21.md',
      'CURRENT_STATUS.md', 'v0.1 NOT RELEASE READY'],
    forbidden: [],
  },
  {
    path: 'docs/CURRENT_STATUS.md',
    required: ['v0.1 NOT RELEASE READY', 'D4-C10', 'NXT-28', 'Pop!_OS 24.04',
      '2368163e588e6e94f74a748bdc6e162b9c4c6b9a', '生产联网', 'main'],
    forbidden: [/^\*\*发布状态：v0\.1 RELEASE READY/m],
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

/**
 * Documentation relocation guard. Verify relative Markdown links in the two
 * canonical entry points, both root READMEs and the immutable old-index
 * snapshot. No browsing, HTTP or content rewriting; external URLs are skipped.
 * New paths MUST remain within the repository and exist as real files.
 */
export const documentationLinkEntries = [
  'docs/README.md', 'docs/CURRENT_STATUS.md',
  'README.md', 'README.zh.md',
  'docs/governance/documentation-index-snapshot-2026-11-21.md',
];

export function verifyLocalDocumentationLinks(root, entries = documentationLinkEntries) {
  const rootPath = resolve(root);
  for (const path of entries) {
    const file = resolve(rootPath, path);
    if (!existsSync(file)) throw new Error('Documentation link source missing: ' + path);
    const contents = readFileSync(file, 'utf8');
    for (const match of contents.matchAll(/\]\(([^)]+)\)/g)) {
      const raw = match[1].trim();
      const reference = raw.split('#', 1)[0];
      if (!reference || /^(?:[a-z][a-z\d+.-]*:|\/)/i.test(reference)) continue;
      const target = resolve(dirname(file), reference);
      if ((!target.startsWith(rootPath + sep) && target !== rootPath)
          || !existsSync(target)) {
        throw new Error('Broken local documentation link: ' + path + ' -> ' + raw);
      }
    }
  }
  return true;
}

const here = fileURLToPath(import.meta.url);
if (process.argv[1] && resolve(process.argv[1]) === here) {
  const root = resolve(dirname(here), '../../..');
  verifyDocumentationBaseline(root);
  verifyLocalDocumentationLinks(root);
  console.log('PASS: Next.js canonical documentation, release boundaries and local links verified');
}
