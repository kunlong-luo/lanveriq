import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // Self-hosted behind a separately accepted Caddy HTTPS origin.
  // Deliberately NO API rewrites, auth proxies or remote image hosts.
  output: 'standalone',
  // NXT-11: immutable public JS/CSS is not session-gated, but browser MIME
  // sniffing must be disabled even when Next is addressed on its private
  // origin (independent of Caddy's public HTTPS response headers).
  async headers() {
    return [{
      source: '/_next/static/:path*',
      headers: [{ key: 'X-Content-Type-Options', value: 'nosniff' }],
    }];
  },
};

export default nextConfig;
