import type { NextConfig } from 'next';

// GitHub Pages hosts ONLY a build-time static, disconnected public preview.
// The ordinary private Node runtime keeps its standalone build and NXT-11
// private-origin security header; never enable this for real authenticated UI.
const pagesPreview = process.env.LANVERIQ_PAGES_STATIC_EXPORT === '1';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // No API rewrites, authentication proxy, remote image hosts or live data.
  output: pagesPreview ? 'export' : 'standalone',
  ...(pagesPreview ? {
    basePath: '/lanveriq',
    trailingSlash: true,
    images: { unoptimized: true },
  } : {
    // Immutable public assets must be nosniff even on the private origin.
    async headers() {
      return [{
        source: '/_next/static/:path*',
        headers: [{ key: 'X-Content-Type-Options', value: 'nosniff' }],
      }];
    },
  }),
};

export default nextConfig;
