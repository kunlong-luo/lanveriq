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
    path: 'AGENTS.md',
    required: ['# Lanveriq — AI agent rules (CURRENT)', 'v0.1 NOT RELEASE READY',
      'docs/governance/engineering-playbook.md', 'default-OFF'],
    forbidden: [/D0-approved V0\.1 architecture \(not yet compiled, deployed or tested\)/i],
  },
  {
    path: 'CONTRIBUTING.md',
    required: ['# Contributing', 'v0.1 NOT RELEASE READY',
      'docs/governance/engineering-playbook.md', 'real synthetic CI'],
    forbidden: [/has not yet shipped functioning code/i,
      /only Rust\/Java\/React unit tests/],
  },
  {
    path: 'SECURITY.md',
    required: ['# Lanveriq security policy', 'v0.1 NOT RELEASE READY',
      'docs/security/fido-trust-boundaries.md'],
    forbidden: [/^Unit-only CI does not replace/m],
  },
  {
    path: 'docs/governance/engineering-playbook.md',
    required: ['ACTIVE', 'Pop!_OS 24.04', 'CURRENT_STATUS',
      'NOT RELEASE READY'],
    forbidden: [],
  },
  {
    path: 'docs/governance/document-lifecycle.md',
    required: ['ACTIVE', 'LIVE-GAP', 'HISTORICAL', 'docs-governance workflow'],
    forbidden: [],
  },
  {
    path: 'docs/planning/release-gap-register.md',
    required: ['LIVE-GAP', 'v0.1 NOT RELEASE READY',
      'G-01', 'G-02', 'G-03', 'G-04', 'G-05', 'G-06', 'G-07', 'G-08', 'OPEN'],
    forbidden: [],
  },
  {
    path: 'docs/planning/definition-of-done.md',
    required: ['Definition of Done', 'v0.1 NOT RELEASE READY',
      'CURRENT_STATUS', 'release-gap register'],
    forbidden: [],
  },
  {
    path: 'docs/security/fido-trust-boundaries.md',
    required: ['FIDO MDS', 'C16', 'NOT RELEASE READY', 'UntrustedSourceEvidence'],
    forbidden: [],
  },
  {
    path: 'docs/architecture/adrs/README.md',
    required: ['ADR', '0008', '0009', 'Supersedes', 'NOT RELEASE READY'],
    forbidden: [],
  },
  {
    path: 'docs/governance/documentation-audit-2026-11-22.md',
    required: ['整改', 'AGENTS', 'CONTRIBUTING',
      'NOT RELEASE READY', 'docs-governance workflow'],
    forbidden: [],
  },
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
  'docs/governance/repository-layout-audit-2026-11-24.md',
  'docs/planning/project-scorecard.md',
  'apps/web/README.md',
  'apps/server/README.md',
  'apps/agent/README.md',
  'apps/desktop/README.md',
  'AGENTS.md',
  'CONTRIBUTING.md',
  'SECURITY.md',
  'docs/governance/engineering-playbook.md',
  'docs/governance/document-lifecycle.md',
  'docs/governance/documentation-audit-2026-11-22.md',
  'docs/planning/release-gap-register.md',
  'docs/planning/definition-of-done.md',
  'docs/security/fido-trust-boundaries.md',
  'docs/architecture/adrs/README.md',
];

export const requiredRepositoryPaths = [
  'apps/web/package.json',
  'apps/server/build.gradle',
  'apps/agent/Cargo.toml',
  'apps/desktop/src-tauri/Cargo.toml',
  'deploy/caddy/Caddyfile.reference',
  'deploy/network/verify_dual_access.py',
  'deploy/rustdesk/client-release-lock.json',
  'deploy/rustdesk-synthetic/verify_rd2_release_boundary.py',
  '.github/workflows/docs-governance.yml',
  'docs/governance/repository-layout-audit-2026-11-24.md',
  'docs/planning/project-scorecard.md',
];

export const retiredRepositoryPaths = [
  'apps/web-next',
  'apps/web-old',
  'apps/web-legacy',
  'apps/web-vite',
  'apps/server/pom.xml',
  'apps/server/mvnw',
  'pom.xml',
  'mvnw',
  'docs/development/pagevela-reference.md',
];

const conciseEntrypoints = [
  'README.md', 'README.zh.md', 'docs/README.md',
  'apps/web/README.md', 'apps/server/README.md',
  'apps/agent/README.md', 'apps/desktop/README.md',
];

export function verifyRepositoryLayout(root) {
  for (const path of requiredRepositoryPaths) {
    if (!existsSync(join(root, path))) {
      throw new Error('Required canonical repository path missing: ' + path);
    }
  }
  for (const path of retiredRepositoryPaths) {
    if (existsSync(join(root, path))) {
      throw new Error('Retired duplicate/obsolete repository path reintroduced: ' + path);
    }
  }
  for (const path of conciseEntrypoints) {
    const contents = readFileSync(join(root, path), 'utf8');
    if (contents.split('\n').length > 140) {
      throw new Error('README grew into outdated chronology: ' + path);
    }
    if (!contents.includes('v0.1 NOT RELEASE READY')) {
      throw new Error('README omits default-deny release truth: ' + path);
    }
  }
  for (const path of ['README.md', 'README.zh.md']) {
    const contents = readFileSync(join(root, path), 'utf8');
    for (const key of ['apps/web/', 'apps/server/', 'apps/agent/',
                       'apps/desktop/', 'deploy/caddy/', 'deploy/network/',
                       'deploy/rustdesk/', 'deploy/rustdesk-synthetic/',
                       'docs/', '.github/workflows/']) {
      if (!contents.includes(key)) {
        throw new Error('Root README missing directory purpose: ' + key);
      }
    }
  }
  return true;
}

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
  verifyRepositoryLayout(root);
  console.log('PASS: Next.js canonical documentation, release boundaries, directory inventory and local links verified');
}
