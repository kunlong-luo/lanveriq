import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { verifySingleUi } from './verify-single-ui.mjs';

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'lanveriq-single-ui-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const write = (path, content) => {
    const filename = join(root, path);
    mkdirSync(resolve(filename, '..'), { recursive: true });
    writeFileSync(filename, content);
  };
  write('apps/web-next/package.json', JSON.stringify({
    name: '@lanveriq/web-next',
    scripts: { dev: 'next dev --hostname 127.0.0.1 --port 3100', build: 'next build' },
    dependencies: { next: '16.4.5' },
  }));
  write('README.md', 'Run apps/web-next on port 3100');
  write('README.zh.md', '从 apps/web-next 启动，端口 3100');
  return { root, write };
}

test('allows a single canonical Next.js 16 frontend', t => {
  const { root } = fixture(t);
  assert.equal(verifySingleUi(root), true);
});

test('rejects resurrected legacy Vite app even if the new UI remains', t => {
  const { root, write } = fixture(t);
  write('apps/web/package.json', '{"name":"@lanveriq/web"}');
  assert.throws(() => verifySingleUi(root), /Legacy apps\/web exists/);
});

test('rejects outdated Vite CI or production runner', t => {
  const { root, write } = fixture(t);
  write('.github/workflows/web-unit.yml', 'name: Web synthetic preview');
  assert.throws(() => verifySingleUi(root), /Legacy Vite CI exists/);
});

test('rejects old local launch docs and dead README link', t => {
  const { root, write } = fixture(t);
  write('README.zh.md', '从 apps/web-next 启动，端口 3100\ncd apps/web\n');
  assert.throws(() => verifySingleUi(root), /active legacy UI launch instruction/);
  write('README.zh.md', '从 apps/web-next 启动，端口 3100');
  write('README.md', 'Use apps/web-next on 3100 and [old](apps/web/README.md)');
  assert.throws(() => verifySingleUi(root), /active legacy UI launch instruction/);
});

test('rejects obsolete Vite dev command replacing pinned Next.js', t => {
  const { root, write } = fixture(t);
  write('apps/web-next/package.json', JSON.stringify({
    name: '@lanveriq/web-next',
    scripts: { dev: 'vite', build: 'next build' },
    dependencies: { next: '16.4.5' },
  }));
  assert.throws(() => verifySingleUi(root), /next dev/);
});

test('real repository contains only the canonical Next.js UI', () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
  assert.equal(verifySingleUi(root), true);
});
