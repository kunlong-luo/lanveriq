/**
 * Canonical HTTPS console origin, matching the backend ConsoleOriginPolicy.
 *
 * This is a browser-side fail-closed guard, NOT proof of TLS, user consent,
 * permissions or an authenticated Spring session. No localhost/HTTP
 * exceptions are permitted for business requests.
 */
export function requireConsoleOrigin(configured: string): URL {
  let url: URL;
  try {
    url = new URL(configured);
  } catch {
    throw new Error('Canonical HTTPS console origin required');
  }
  const host = url.hostname;
  if (url.protocol !== 'https:' ||
      !/^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/.test(host) ||
      !host.includes('.') || /^[0-9.]+$/.test(host) ||
      configured !== `https://${host}` ||
      url.username || url.password || url.port ||
      url.pathname !== '/' || url.search || url.hash) {
    throw new Error('Canonical HTTPS console origin required');
  }
  return url;
}
