import { defineConfig } from 'orval';

// Source of truth is the real Springdoc build artifact, NOT a copied fixture.
// This tool runs only in CI. It does not publish browser code or enable API routes.
export default defineConfig({
  lanveriqConsole: {
    input: {
      target: '../build/contracts/openapi.json',
    },
    output: {
      target: './generated/lanveriq.ts',
      schemas: './generated/model',
      mode: 'split',
      client: 'react-query',
      httpClient: 'fetch',
      override: {
        query: {
          useQuery: true,
        },
      },
    },
  },
});
