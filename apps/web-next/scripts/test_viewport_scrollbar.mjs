import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const css = readFileSync(
  new URL('../src/app/globals.css', import.meta.url), 'utf8',
);

test('viewport hides native vertical scrollbar in Firefox and Chromium/WebKit', () => {
  assert.match(css, /html,\s*body\s*\{\s*scrollbar-width:\s*none;/);
  assert.match(css, /html::\-webkit-scrollbar,\s*body::\-webkit-scrollbar\s*\{\s*display:\s*none;/);
});

test('scrolling remains enabled and no document-wide clipping is introduced', () => {
  assert.doesNotMatch(css, /html\s*,\s*body\s*\{[^}]*overflow:\s*hidden/i);
  assert.doesNotMatch(css, /html\s*\{[^}]*overflow-y:\s*hidden/i);
  assert.doesNotMatch(css, /body\s*\{[^}]*overflow-y:\s*hidden/i);
});
