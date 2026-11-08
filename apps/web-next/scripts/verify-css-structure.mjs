/**
 * NXT-18: source-only CSS selector-overlap guard for the consolidated design
 * language. It does not parse or normalize values, reorder declarations or
 * import runtime styles. No network, browser or external dependencies.
 *
 * A CSS selector may legitimately recur in DIFFERENT media-query contexts,
 * but a repeated exact selector within the SAME context almost always means
 * accidental layered overrides that can drift over time.
 */

function skipCssTrivia(css, start, end) {
  let i = start;
  while (i < end && /\s/.test(css[i])) i += 1;
  return i;
}

/** @returns {string[]} repeated selector/context keys */
export function findDuplicateRules(css) {
  const source = css.replace(/\/\*[\s\S]*?\*\//g, match => ' '.repeat(match.length));
  const counts = new Map();

  function walk(start, end, context) {
    let i = start;
    while (i < end) {
      i = skipCssTrivia(source, i, end);
      const headStart = i;
      // Authoring contract: braces in quoted declaration values are not used
      // in this application's design CSS. Quotes are still handled correctly.
      let quote = '';
      let escaped = false;
      while (i < end) {
        const c = source[i];
        if (quote) {
          if (escaped) escaped = false;
          else if (c === '\\') escaped = true;
          else if (c === quote) quote = '';
        } else if (c === '"' || c === "'") quote = c;
        else if (c === '{' || c === ';') break;
        i += 1;
      }
      if (i >= end) break;
      if (source[i] === ';') { i += 1; continue; }
      const selector = source.slice(headStart, i).trim().replace(/\s+/g, ' ');
      let depth = 1;
      let j = i + 1;
      quote = '';
      escaped = false;
      for (; j < end && depth !== 0; j += 1) {
        const c = source[j];
        if (quote) {
          if (escaped) escaped = false;
          else if (c === '\\') escaped = true;
          else if (c === quote) quote = '';
        } else if (c === '"' || c === "'") quote = c;
        else if (c === '{') depth += 1;
        else if (c === '}') depth -= 1;
      }
      if (depth !== 0) throw new Error('NXT-18 malformed/unbalanced CSS declaration blocks');
      if (/^@(media|supports|container)\b/.test(selector)) {
        walk(i + 1, j - 1, context + ' > ' + selector);
      } else if (selector && !selector.startsWith('@')) {
        // This intentionally treats grouped selectors as one authored unit
        // rather than trying to infer equal specificity across aliases.
        const key = context + ' :: ' + selector;
        counts.set(key, (counts.get(key) ?? 0) + 1);
      }
      i = j;
    }
  }
  walk(0, source.length, 'root');
  return [...counts].filter(([, count]) => count > 1).map(([key]) => key).sort();
}

export function verifyStyleStructure(css) {
  const duplicates = findDuplicateRules(css);
  if (duplicates.length) {
    throw new Error('NXT-18 duplicate same-scope design selectors: ' + duplicates.join(' | '));
  }
  const tokens = ['--background', '--foreground', '--border', '--lan-violet', '--bc-radius'];
  for (const token of tokens) {
    if (!css.includes(token + ':')) throw new Error('Missing NXT-18 design token ' + token);
  }
  return true;
}
