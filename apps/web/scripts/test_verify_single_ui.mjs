import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { verifySingleUi } from './verify-single-ui.mjs';

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'lanveriq-single-web-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const write = (path, content) => {
    const filename = join(root, path);
    mkdirSync(dirname(filename), { recursive: true });
    writeFileSync(filename, content);
  };
  write('apps/web/package.json', JSON.stringify({
    name: '@lanveriq/web',
    scripts: { dev: 'next dev --hostname 127.0.0.1 --port 3100', build: 'next build' },
    dependencies: { next: '16.4.5' },
  }));
  write('.github/workflows/web-unit.yml',
    "name: Next.js fast source and preview checks\non:\n  push:\n    paths:\n      - 'apps/web/**'\n");
  write('README.md', 'Run apps/web on port 3100');
  write('README.zh.md', '从 apps/web 启动，端口 3100');
  return { root, write };
}

test('allows the only canonical apps/web Next.js 16 frontend', t => {
  const { root } = fixture(t);
  assert.equal(verifySingleUi(root), true);
});

test('rejects resurrected web-next alongside the canonical UI', t => {
  const { root, write } = fixture(t);
  write('apps/web-next/package.json', '{"name":"@lanveriq/web-next"}');
  assert.throws(() => verifySingleUi(root), /Obsolete apps\/web-next directory/);
});

test('rejects old Next.js workflow path after rename', t => {
  const { root, write } = fixture(t);
  write('.github/workflows/web-next-unit.yml', 'name: obsolete');
  assert.throws(() => verifySingleUi(root), /Obsolete web-next-unit workflow/);
});

test('rejects Vite being reintroduced inside apps/web', t => {
  const { root, write } = fixture(t);
  write('apps/web/vite.config.ts', 'export default {}');
  assert.throws(() => verifySingleUi(root), /Legacy Vite config/);
});

test('rejects stale web-next launch instructions and 5173', t => {
  const { root, write } = fixture(t);
  write('README.zh.md', '启动 apps/web:3100\ncd apps/web-next\n');
  assert.throws(() => verifySingleUi(root), /obsolete web-next launch\/link/);
  write('README.zh.md', '从 apps/web 启动，端口 3100');
  write('README.md', 'Run apps/web on 3100 and [old](apps/web-next/README.md)');
  assert.throws(() => verifySingleUi(root), /obsolete web-next launch\/link/);
  write('README.md', 'Run apps/web on 3100\nhttp://127.0.0.1:5173');
  assert.throws(() => verifySingleUi(root), /Vite port present/);
});

test('rejects Vite dev command and dependencies replacing Next.js', t => {
  const { root, write } = fixture(t);
  write('apps/web/package.json', JSON.stringify({
    name: '@lanveriq/web',
    scripts: { dev: 'vite', build: 'next build' },
    dependencies: { next: '16.4.5', vite: '8.3.5' },
  }));
  assert.throws(() => verifySingleUi(root), /next dev/);
});

test('real repository contains only canonical apps/web Next.js', () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
  assert.equal(verifySingleUi(root), true);
});
