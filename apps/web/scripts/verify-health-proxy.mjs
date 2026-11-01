/**
 * WEB-01-HEALTH: isolated real Vite dev-proxy acceptance with a synthetic
 * loopback-only HTTP service. Never touches the user's Java/PostgreSQL process.
 */
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createServer as createHttpServer } from 'node:http';
import { createServer as createViteServer } from 'vite';

const requests = [];
const backend = createHttpServer((request, response) => {
  requests.push({ method: request.method, path: request.url });
  if (request.method !== 'GET' || request.url !== '/actuator/health') {
    response.writeHead(403).end('Only synthetic health is exposed');
    return;
  }
  response.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
  response.end(JSON.stringify({ status: 'UP' }));
});
backend.listen(0, '127.0.0.1');
await once(backend, 'listening');

let vite;
try {
  const address = backend.address();
  assert.ok(address && typeof address === 'object');
  process.env.LANVERIQ_WEB_HEALTH_PORT = String(address.port);
  vite = await createViteServer({
    logLevel: 'error',
    server: { host: '127.0.0.1', port: 0, strictPort: false },
  });
  await vite.listen();
  const frontAddress = vite.httpServer.address();
  assert.ok(frontAddress && typeof frontAddress === 'object');
  const origin = `http://127.0.0.1:${frontAddress.port}`;

  const health = await fetch(`${origin}/actuator/health`, { redirect: 'manual' });
  assert.equal(health.status, 200);
  assert.deepEqual(await health.json(), { status: 'UP' });
  assert.deepEqual(requests, [{ method: 'GET', path: '/actuator/health' }]);

  const rejectedWrite = await fetch(`${origin}/actuator/health`, { method: 'POST', redirect: 'manual' });
  assert.equal(rejectedWrite.status, 405, 'even health must never forward a write method');
  assert.deepEqual(requests, [{ method: 'GET', path: '/actuator/health' }]);

  // Vite may render an SPA fallback for unknown URLs; that is NOT an upstream
  // business API response. Verify nothing is forwarded to the fake backend.
  await fetch(`${origin}/api/v1/devices`, { redirect: 'manual' });
  await fetch(`${origin}/actuator/health-extra`, { redirect: 'manual' });
  assert.deepEqual(requests, [{ method: 'GET', path: '/actuator/health' }]);

  console.log('PASS: real Vite proxy forwards only exact anonymous health GET to synthetic loopback backend; business routes never proxied.');
} finally {
  if (vite) await vite.close();
  await new Promise((resolve, reject) =>
    backend.close(error => error ? reject(error) : resolve()),
  );
}
