import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { verifyRuntimeIsolation } from './verify-runtime-isolation.mjs';

// Synthetic files are created in a disposable tree, never inside the real
// checked-in App Router; tests do not execute imported code or use network.
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'lanveriq-route-graph-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  for (const sub of ['src/app', 'src/components', 'src/lib',
                     'src/lib/.generated-contract']) {
    mkdirSync(join(root, sub), { recursive: true });
  }
  const write = (path, body) => {
    const absolute = join(root, path);
    mkdirSync(join(absolute, '..'), { recursive: true });
    writeFileSync(absolute, body, 'utf8');
  };
  write('src/app/page.tsx', `import { Preview } from '@/components/preview';
export default function Page(){return Preview()}`);
  write('src/components/preview.tsx', `export function Preview(){return null}`);
  write('src/lib/staged-console-auth.ts', `export const secret = 1`);
  write('src/lib/staged-console-read.ts', `export const read = 1`);
  write('src/staged-console-workspace.tsx', `export const Workspace = 1`);
  write('src/staged-console-route-boundary.tsx', `export const Boundary = 1`);
  return { root, write };
}

test('ordinary route can follow safe reexports, alias and relative modules', t => {
  const {root, write} = fixture(t);
  write('src/lib/format.ts', `export const harmless = 'staged-console-auth'; // comment only`);
  write('src/components/preview.tsx', `import { harmless } from '../lib/format';
export function Preview(){return harmless.length}`);
  const result = verifyRuntimeIsolation(root);
  assert.ok(result.checkedEntries >= 2);
  assert.ok(result.checkedModules >= 3);
});

test('detects direct import of staged authentication from app', t => {
  const {root, write} = fixture(t);
  write('src/app/page.tsx', `import { secret } from '../lib/staged-console-auth';
export default function Page(){return secret}`);
  assert.throws(() => verifyRuntimeIsolation(root),
    /Protected staged Console module reachable/);
});

test('detects transitive alias imports through an innocent library', t => {
  const {root, write} = fixture(t);
  write('src/lib/relay.ts', `import { read } from '@/lib/staged-console-read';
export const relay = read`);
  write('src/components/preview.tsx', `export { relay as Preview } from '../lib/relay'`);
  assert.throws(() => verifyRuntimeIsolation(root),
    /components\/preview\.tsx.*lib\/relay\.ts.*staged-console-read/);
});

test('detects namespace reexports to the staged product-shaped UI', t => {
  const {root, write} = fixture(t);
  write('src/lib/relay.ts', `export * from '../staged-console-workspace'`);
  write('src/app/page.tsx', `export { Workspace as default } from '../lib/relay'`);
  assert.throws(() => verifyRuntimeIsolation(root),
    /Protected staged Console module reachable/);
});

test('blocks transitive import of new session-checked multi-route boundary', t => {
  const {root, write} = fixture(t);
  write('src/components/preview.tsx', `export { Boundary as Preview } from '../staged-console-route-boundary'`);
  assert.throws(() => verifyRuntimeIsolation(root),
    /Protected staged Console module reachable/);
});

test('detects import type, import() type and literal dynamic import', t => {
  const samples = [
    `import type { secret } from '@/lib/staged-console-auth';`,
    `type Payload = import('@/lib/staged-console-read').read;`,
    `export const load = () => import('../lib/staged-console-read');`,
    `const active = require('../lib/staged-console-auth');`,
  ];
  for (const source of samples) {
    const { root, write } = fixture(t);
    write('src/app/page.tsx', source);
    assert.throws(() => verifyRuntimeIsolation(root),
      /Protected staged Console module reachable/);
  }
});

test('detects source-generated API contract imports via reexport chain', t => {
  const {root, write} = fixture(t);
  write('src/lib/.generated-contract/types.ts', `export type Device = string`);
  write('src/lib/relay.ts', `export type {Device} from './.generated-contract/types'`);
  write('src/components/preview.tsx', `import type {Device} from '../lib/relay';
export function Preview(){const value: Device = 'x'; return value}`);
  assert.throws(() => verifyRuntimeIsolation(root),
    /Protected staged Console module reachable/);
});

test('rejects computed dynamic import or require, not just known strings', t => {
  for (const code of [
    `const target = '../lib/staged-console-read'; import(target)`,
    `const target = '../lib/staged-console-auth'; require(target)`,
  ]) {
    const {root, write} = fixture(t);
    write('src/app/page.tsx', code);
    assert.throws(() => verifyRuntimeIsolation(root),
      /Computed import\/require is forbidden/);
  }
});

test('fails closed on unresolved local imports and path escapes', t => {
  for (const code of [
    `import './unknown'`,
    `import '../../other-code.ts'`,
  ]) {
    const {root, write} = fixture(t);
    write('src/app/page.tsx', code);
    assert.throws(() => verifyRuntimeIsolation(root),
      /Unresolved local|escaped the source tree/);
  }
});

test('CSS side-effect and installed external package imports remain permitted', t => {
  const {root, write} = fixture(t);
  write('src/app/page.tsx', `import 'next/link';
import './globals.css';
import { Preview } from '@/components/preview';
export default function Page(){ return Preview() }`);
  assert.doesNotThrow(() => verifyRuntimeIsolation(root));
});

test('no source code is executed: dangerous module body is never evaluated', t => {
  const {root, write} = fixture(t);
  write('src/lib/malicious.ts', `throw new Error('import body executed');`);
  write('src/components/preview.tsx', `import '../lib/malicious';
export function Preview(){return null}`);
  assert.doesNotThrow(() => verifyRuntimeIsolation(root));
});
