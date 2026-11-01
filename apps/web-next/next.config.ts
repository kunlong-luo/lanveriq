import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // Self-hosted behind a separately accepted Caddy HTTPS origin.
  // Deliberately NO API rewrites, auth proxies or remote image hosts.
  output: 'standalone',
};

export default nextConfig;
