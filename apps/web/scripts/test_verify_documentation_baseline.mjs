import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { documentationRequirements, verifyDocumentationBaseline } from './verify-documentation-baseline.mjs';

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
  const record = documentationRequirements[0];
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
