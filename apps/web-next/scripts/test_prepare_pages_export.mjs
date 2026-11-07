import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, existsSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { preparePagesExport } from './prepare-pages-export.mjs';

function makeWorkspace(callback) {
  const temp = mkdtempSync(join(tmpdir(), 'lanveriq-pages-test-'));
  const root = join(temp, 'web-next');
  const detail = join(root, 'src/app/(console)/devices/[id]');
  mkdirSync(detail, { recursive: true });
  const marker = 'notFound();\nconst title = "设备详情（未连接）";';
  writeFileSync(join(detail, 'page.tsx'), marker, 'utf8');
  try { callback({ root, detail, marker }); }
  finally { rmSync(temp, { recursive: true, force: true }); }
}

const acceptedEnv = {
  GITHUB_ACTIONS: 'true',
  LANVERIQ_PAGES_STATIC_EXPORT: '1',
};

test('rejects running outside both GitHub Actions and Pages static gate', () => {
  makeWorkspace(({ root, detail }) => {
    for (const env of [
      {},
      { ...acceptedEnv, GITHUB_ACTIONS: 'false' },
      { ...acceptedEnv, LANVERIQ_PAGES_STATIC_EXPORT: '0' },
    ]) {
      assert.throws(() => preparePagesExport({ root, env }));
      assert.equal(existsSync(detail), true);
    }
  });
});

test('only relocates the disposable unconnected dynamic route once', () => {
  makeWorkspace(({ root, detail, marker }) => {
    const target = preparePagesExport({ root, env: acceptedEnv });
    assert.equal(existsSync(detail), false);
    assert.equal(readFileSync(join(target, 'page.tsx'), 'utf8'), marker);
    assert.throws(() => preparePagesExport({ root, env: acceptedEnv }));
  });
});

test('refuses to alter a changed or authenticated device detail route', () => {
  makeWorkspace(({ root, detail }) => {
    writeFileSync(join(detail, 'page.tsx'), 'export const liveAuth = true;', 'utf8');
    assert.throws(() => preparePagesExport({ root, env: acceptedEnv }));
    assert.equal(existsSync(detail), true);
  });
});
