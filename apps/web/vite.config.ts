import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// The ONLY backend proxy in development: unauthenticated, read-only health.
// Never proxy /api, browser login or device/employee data before TLS/CSRF acceptance.
const healthPort = Number(process.env.LANVERIQ_WEB_HEALTH_PORT ?? '8080');
if (!Number.isInteger(healthPort) || healthPort < 1024 || healthPort > 65535) {
  throw new Error('LANVERIQ_WEB_HEALTH_PORT must be a local unprivileged TCP port');
}

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    // The proxy must not forward POST/PUT/DELETE even to the health endpoint.
    // Vite's configureServer middleware executes before its internal proxy.
    {
      name: 'lanveriq-dev-health-get-only',
      apply: 'serve',
      configureServer(server) {
        server.middlewares.use((request, response, next) => {
          if (/^\/actuator\/health(?:\?.*)?$/.test(request.url ?? '') && request.method !== 'GET') {
            response.writeHead(405, { Allow: 'GET', 'Cache-Control': 'no-store' });
            response.end();
            return;
          }
          next();
        });
      },
    },
  ],
  resolve: { alias: { '@': fileURLToPath(new URL('./src/', import.meta.url)) } },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '^/actuator/health(?:\\?.*)?$': {
        target: `http://127.0.0.1:${healthPort}`,
        changeOrigin: false,
        ws: false,
      },
    },
  },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true },
  build: { sourcemap: false },
});
