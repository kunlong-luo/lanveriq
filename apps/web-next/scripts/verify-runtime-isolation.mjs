import ts from 'typescript-ast';
import { existsSync, readFileSync, realpathSync, readdirSync, statSync } from 'node:fs';
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from 'node:path';

/**
 * Verify the transitive IMPORT GRAPH from normal Next.js routes and shared
 * components. A simple grep of src/app misses app -> helper -> staged auth.
 *
 * Uses the already locked, mature TypeScript compiler parser instead of a
 * hand-rolled JavaScript import regex. It does not execute application code,
 * fetch data, emit files, or mount any authentication/UI route.
 */
const SOURCE_EXTENSIONS = ['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs'];
const ASSET_EXTENSIONS = new Set(['.css', '.scss', '.sass', '.less', '.svg',
  '.png', '.jpg', '.jpeg', '.webp', '.woff', '.woff2', '.ico']);

function within(path, parent) {
  return path === parent || path.startsWith(parent + sep);
}

function protectedModule(rel) {
  const path = rel.replaceAll('\\', '/');
  return path === 'src/lib/staged-console-auth.ts' ||
    path === 'src/lib/staged-console-read.ts' ||
    path === 'src/staged-console-workspace.tsx' ||
    path === 'src/synthetic-only-auth-probe.tsx' ||
    path === 'src/synthetic-only-multi-admin-probe.tsx' ||
    path === 'src/synthetic-only-auth-probe.test.tsx' ||
    path.startsWith('src/lib/.generated-contract/') ||
    path.startsWith('src/.generated-contract/');
}

function localSpecifier(specifier) {
  return specifier.startsWith('.') || specifier.startsWith('@/') ||
    specifier.startsWith('src/');
}

function resolveLocalModule(root, source, specifier) {
  const src = resolve(root, 'src');
  const base = specifier.startsWith('@/')
    ? resolve(src, specifier.slice(2))
    : specifier.startsWith('src/')
      ? resolve(root, specifier)
      : resolve(dirname(source), specifier);

  if (!within(base, src)) {
    throw new Error('Next.js runtime import escaped the source tree');
  }
  const directExtension = extname(base).toLowerCase();
  if (ASSET_EXTENSIONS.has(directExtension)) return null;
  const candidates = directExtension
    ? [base]
    : [...SOURCE_EXTENSIONS.map(x => base + x),
       ...SOURCE_EXTENSIONS.map(x => join(base, 'index' + x))];

  for (const candidate of candidates) {
    if (!existsSync(candidate)) continue;
    const real = realpathSync(candidate);
    if (!within(real, src)) {
      throw new Error('Next.js runtime import escaped the source tree via symlink');
    }
    if (statSync(real).isFile()) return real;
  }
  throw new Error('Unresolved local Next.js runtime import: ' +
    relative(root, source).replaceAll('\\', '/'));
}

function importSpecifiers(source) {
  const document = ts.createSourceFile('runtime.tsx', source, ts.ScriptTarget.Latest,
    true, ts.ScriptKind.TSX);
  const found = [];
  function literal(node) {
    return node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node))
      ? node.text : null;
  }
  function visit(node) {
    if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
        node.moduleSpecifier) {
      const value = literal(node.moduleSpecifier);
      if (value === null) throw new Error('Unsupported non-literal module specifier');
      found.push(value);
    } else if (ts.isImportEqualsDeclaration(node) &&
               ts.isExternalModuleReference(node.moduleReference)) {
      const value = literal(node.moduleReference.expression);
      if (value === null) throw new Error('Unsupported imported module reference');
      found.push(value);
    } else if (ts.isCallExpression(node) &&
               (node.expression.kind === ts.SyntaxKind.ImportKeyword ||
                (ts.isIdentifier(node.expression) &&
                 node.expression.text === 'require'))) {
      const value = node.arguments.length === 1 ? literal(node.arguments[0]) : null;
      if (value === null) {
        // Dynamic import/require can hide a protected runtime module. Refuse
        // non-literal module loading rather than trying to guess its target.
        throw new Error('Computed import/require is forbidden in Next.js runtime');
      }
      found.push(value);
    } else if (ts.isImportTypeNode(node)) {
      const argument = node.argument;
      if (ts.isLiteralTypeNode(argument)) {
        const value = literal(argument.literal);
        if (value !== null) found.push(value);
      }
    }
    ts.forEachChild(node, visit);
  }
  visit(document);
  return found;
}

function entries(folder) {
  const collected = [];
  function walk(dir) {
    if (!existsSync(dir)) return;
    for (const item of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, item.name);
      if (item.isSymbolicLink()) {
        throw new Error('Symlink forbidden in checked Next.js runtime source');
      }
      if (item.isDirectory()) walk(path);
      else if (SOURCE_EXTENSIONS.includes(extname(item.name))) collected.push(path);
    }
  }
  walk(folder);
  return collected;
}

export function verifyRuntimeIsolation(root) {
  const project = realpathSync(resolve(root));
  const src = resolve(project, 'src');
  if (!existsSync(src)) throw new Error('Missing Next.js source tree');
  const roots = [join(src, 'app'), join(src, 'components')];
  for (const folder of roots) {
    if (!existsSync(folder) || !statSync(folder).isDirectory()) {
      throw new Error('Missing Next.js runtime entry folder');
    }
  }
  const seen = new Set();
  const visit = (file, stack) => {
    const rel = relative(project, file).replaceAll('\\', '/');
    if (protectedModule(rel)) {
      throw new Error('Protected staged Console module reachable from normal route: ' +
        [...stack, rel].join(' -> '));
    }
    if (seen.has(file)) return;
    seen.add(file);
    const source = readFileSync(file, 'utf8');
    for (const imported of importSpecifiers(source)) {
      if (!localSpecifier(imported)) continue;
      const resolved = resolveLocalModule(project, file, imported);
      if (resolved) visit(resolved, [...stack, rel]);
    }
  };
  const starts = roots.flatMap(entries);
  for (const start of starts) visit(realpathSync(start), []);
  return { checkedEntries: starts.length, checkedModules: seen.size };
}
