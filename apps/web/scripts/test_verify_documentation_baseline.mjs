import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { documentationRequirements, verifyDocumentationBaseline,
  verifyLocalDocumentationLinks, verifyRepositoryLayout,
  requiredRepositoryPaths, conciseLivePages,
  verifyNoRetiredSnapshotLinks, verifyAllDocumentationLinks } from './verify-documentation-baseline.mjs';

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'lanveriq-docs-current-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const write = (file, text) => {
    const absolute = join(root, file);
    mkdirSync(dirname(absolute), { recursive: true });
    writeFileSync(absolute, text, 'utf8');
  };
  for (const record of documentationRequirements) {
    write(record.path, record.required.join('\n'));
  }
  return { root, write };
}

test('accepts the canonical documented stack and migration boundary', t => {
  const { root } = fixture(t);
  assert.equal(verifyDocumentationBaseline(root), true);
});

test('rejects missing authoritative documentation rather than skipping it', t => {
  const { root } = fixture(t);
  rmSync(join(root, 'docs/development/technology.md'));
  assert.throws(() => verifyDocumentationBaseline(root), /Current documentation missing/);
});

test('rejects stale status and missing canonical UI tokens', t => {
  const { root, write } = fixture(t);
  write('docs/development/frontend-design-system.md', '2026-11-08 B+C purple #6A5BFF Vite');
  assert.throws(() => verifyDocumentationBaseline(root), /Current documentation drift/);
});

test('rejects duplicate obsolete Vite directory guidance', t => {
  const { root, write } = fixture(t);
  const record = documentationRequirements.find(r => r.path === 'docs/development/standards.md');
  assert.ok(record);
  write(record.path, record.required.join('\n') +
    '\n- `apps/web/`：历史 React/Vite 候选，仅供参考\n');
  assert.throws(() => verifyDocumentationBaseline(root), /Obsolete active guidance/);
});

test('rejects old Vite acceptance matrix after source removal', t => {
  const { root, write } = fixture(t);
  const record = documentationRequirements.find(r => r.path.includes('nextjs-nxt02-manual'));
  assert.ok(record);
  write(record.path, record.required.join('\n') +
    '\n| Regression | Original apps/web preview | Old Vite routes still build independently |\n');
  assert.throws(() => verifyDocumentationBaseline(root), /Obsolete active guidance/);
});

test('checks the actual checked-in documentation without network access', () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
  assert.equal(verifyDocumentationBaseline(root), true);
});

test('current four-app tree has no retired Web/Maven subtree or chronology-sized README', () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
  assert.equal(verifyRepositoryLayout(root), true);
});

test('layout guard refuses a duplicate Web, missing canonical runtime and bloated README', t => {
  const root = mkdtempSync(join(tmpdir(), 'lanveriq-repository-layout-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  function write(path, contents = '') {
    const file = join(root, path);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, contents, 'utf8');
  }
  for (const path of requiredRepositoryPaths) write(path, 'reviewed');
  const entries = [
    'README.md', 'README.zh.md', 'docs/README.md',
    'apps/web/README.md', 'apps/server/README.md',
    'apps/agent/README.md', 'apps/desktop/README.md',
  ];
  const map = [
    'apps/web/', 'apps/server/', 'apps/agent/', 'apps/desktop/',
    'deploy/caddy/', 'deploy/network/', 'deploy/rustdesk/',
    'deploy/rustdesk-synthetic/', 'docs/', '.github/workflows/',
  ];
  for (const path of entries) write(path, 'v0.1 NOT RELEASE READY\n' + map.join(' '));
  for (const { path } of conciseLivePages) write(path, 'v0.1 NOT RELEASE READY\n');
  assert.equal(verifyRepositoryLayout(root), true);
  write('apps/web-next/package.json', 'obsolete');
  assert.throws(() => verifyRepositoryLayout(root),
    /Retired duplicate\/obsolete repository path/);
  rmSync(join(root, 'apps/web-next'), { recursive: true });
  rmSync(join(root, 'apps/server/build.gradle'));
  assert.throws(() => verifyRepositoryLayout(root), /Required canonical repository path missing/);
  write('apps/server/build.gradle', 'reviewed');
  write('README.md', 'v0.1 NOT RELEASE READY\n' +
    Array.from({ length: 145 }, () => map.join(' ')).join('\n'));
  assert.throws(() => verifyRepositoryLayout(root),
    /README grew into outdated chronology/);
  write('README.md', 'v0.1 NOT RELEASE READY\n' + map.join(' '));
  write('docs/CURRENT_STATUS.md',
    'v0.1 NOT RELEASE READY\n' + 'Historical transcript\n'.repeat(80));
  assert.throws(() => verifyRepositoryLayout(root),
    /LIVE documentation expanded into a duplicate CI log/);
  write('docs/CURRENT_STATUS.md',
    'v0.1 NOT RELEASE READY\n## L2B-99: repeated old acceptance transcript\n');
  assert.throws(() => verifyRepositoryLayout(root),
    /Historical milestone transcript reintroduced into LIVE document/);
  write('docs/CURRENT_STATUS.md', 'v0.1 NOT RELEASE READY\n');
  write('docs/planning/release-gap-register.md',
    'v0.1 NOT RELEASE READY\n**2026-11-24 G-05 redundant status**');
  assert.throws(() => verifyRepositoryLayout(root),
    /Historical milestone transcript reintroduced into LIVE document/);
  write('docs/planning/release-gap-register.md', 'v0.1 NOT RELEASE READY\n');
  write('docs/governance/documentation-index-snapshot-2026-11-21.md',
    'duplicate historical docs homepage');
  assert.throws(() => verifyRepositoryLayout(root),
    /Retired duplicate\/obsolete repository path/);
});

test('rejects obsolete no-code contribution instructions', t => {
  const { root, write } = fixture(t);
  const record = documentationRequirements.find(x => x.path === 'CONTRIBUTING.md');
  assert.ok(record);
  write(record.path, record.required.join('\n') +
    '\nThe repository has not yet shipped functioning code.\n');
  assert.throws(() => verifyDocumentationBaseline(root),
    /Obsolete active guidance reintroduced/);
});

test('rejects missing release gate register', t => {
  const { root } = fixture(t);
  rmSync(join(root, 'docs/planning/release-gap-register.md'));
  assert.throws(() => verifyDocumentationBaseline(root),
    /Current documentation missing/);
});

test('rejects broken links in newly active engineering rules', t => {
  const { root, write } = fixture(t);
  write('docs/governance/engineering-playbook.md',
    '[missing release gate](../planning/gap-does-not-exist.md)');
  assert.throws(() => verifyLocalDocumentationLinks(root,
    ['docs/governance/engineering-playbook.md']),
    /Broken local documentation link/);
});

test('current documentation index and two READMEs resolve all relative links', () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
  assert.equal(verifyLocalDocumentationLinks(root), true);
});

test('rejects an archived or current document link after path relocation', t => {
  const { root, write } = fixture(t);
  write('docs/CURRENT_STATUS.md', '[missing](does-not-exist.md)');
  assert.throws(() => verifyLocalDocumentationLinks(root, ['docs/CURRENT_STATUS.md']),
    /Broken local documentation link/);
});

test('allows external citations and existing local files without network requests', t => {
  const { root, write } = fixture(t);
  write('docs/CURRENT_STATUS.md',
    '[GitHub CI](https://github.com/example/example/actions/runs/1) ' +
    '[status](../docs/README.md)');
  assert.equal(verifyLocalDocumentationLinks(root, ['docs/CURRENT_STATUS.md']), true);
});

test('all current docs are free of live relative links to archived duplicate snapshots', () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
  assert.equal(verifyNoRetiredSnapshotLinks(root), true);
});

test('rejects nested documentation links to retired duplicate AGENTS and old index', t => {
  const { root, write } = fixture(t);
  for (const source of ['README.md', 'README.zh.md', 'AGENTS.md', 'CHANGELOG.md']) {
    write(source, 'v0.1 NOT RELEASE READY');
  }
  write('docs/security/references/notes.md',
    '[old AGENTS](../../governance/archive/AGENTS-pre-consolidation-2026-11-22.md)');
  assert.throws(() => verifyNoRetiredSnapshotLinks(root),
    /Retired historical snapshot linked as a live file/);
  write('docs/security/references/notes.md',
    '[old docs index](../../governance/documentation-index-snapshot-2026-11-21.md)');
  assert.throws(() => verifyNoRetiredSnapshotLinks(root),
    /Retired historical snapshot linked as a live file/);
  write('docs/security/references/notes.md',
    '[old plan](../../planning/v0.1-implementation-sequence.md)');
  assert.throws(() => verifyNoRetiredSnapshotLinks(root),
    /Retired historical snapshot linked as a live file/);
  write('docs/security/references/notes.md',
    '[immutable old docs index](https://github.com/kunlong-luo/lanveriq/blob/80a74373/docs/governance/documentation-index-snapshot-2026-11-21.md)');
  assert.equal(verifyNoRetiredSnapshotLinks(root), true);
});


test('recursive link check includes historical Markdown outside curated entry points', t => {
  const { root, write } = fixture(t);
  write('docs/testing/dated-evidence.md',
    '[valid](../architecture/adrs/0008-nextjs-app-router-console.md)\n');
  assert.equal(verifyAllDocumentationLinks(root), true);
  write('docs/testing/dated-evidence.md', '[removed](../development/deleted-snapshot.md)\n');
  assert.throws(() => verifyAllDocumentationLinks(root), /Broken local documentation link/);
});
