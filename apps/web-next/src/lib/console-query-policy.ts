import { QueryClient } from '@tanstack/react-query';

/**
 * NXT-03B: explicit, non-persistent state policy for future Spring
 * session-scoped REST reads. A provider alone DOES NOT enable a network
 * request or authorize a browser, and no runtime Orval hooks are mounted.
 */
export function createConsoleQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnMount: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        staleTime: 0,
        gcTime: 0,
      },
      mutations: { retry: false },
    },
  });
}

/**
 * Must be called when a session is revoked, replaced or expires before
 * rendering any future credentialed data. Do not retain previously
 * authorized rows across principals or device-group permission changes.
 */
export async function clearConsoleSessionState(client: QueryClient): Promise<void> {
  await client.cancelQueries();
  client.clear();
}
