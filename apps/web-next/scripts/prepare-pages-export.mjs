import { existsSync, mkdirSync, readFileSync, renameSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';

/**
 * Only the throwaway GitHub Actions runner may omit the dynamic device detail
 * route for a PUBLIC static export. Do not replace that route with a fake
 * device UUID or weaken the ordinary Next.js server-build behavior.
 *
 * The repository's unchanged source has already passed verify:routes.
 */
export function preparePagesExport({
  root = process.cwd(),
  env = process.env,
} = {}) {
  if (env.GITHUB_ACTIONS !== 'true' ||
      env.LANVERIQ_PAGES_STATIC_EXPORT !== '1' ||
      basename(root) !== 'web-next') {
    throw new Error('Pages export fixture requires explicit GitHub Actions/static gates');
  }
  const source = resolve(root, 'src/app/(console)/devices/[id]');
  const target = resolve(root, '.pages-omitted-device-detail');
  const page = join(source, 'page.tsx');
  if (!existsSync(page) || existsSync(target)) {
    throw new Error('Pages dynamic detail route is missing or previously modified');
  }
  const checkedIn = readFileSync(page, 'utf8');
  if (!checkedIn.includes('设备详情（未连接）') ||
      !checkedIn.includes('notFound()') ||
      checkedIn.includes('generateStaticParams')) {
    throw new Error('Unexpected device detail route; refusing to alter Pages export');
  }
  // Atomic move entirely outside src/app, after the source isolation gate.
  // This runner is disposable; no checked-in files or real data are changed.
  renameSync(source, target);
  return target;
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(new URL(import.meta.url).pathname)) {
  preparePagesExport();
  console.log('PAGES STATIC PREVIEW: dynamic device detail route excluded in disposable CI.');
}
