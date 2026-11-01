'use client';

import { useState, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

/**
 * NXT-03B query context is safe to mount BEFORE data access approval.
 * No useQuery / generated-client import occurs in runtime; this only
 * provides the approved server-state library and conservative defaults.
 *
 * No automatic background retries, focus/reconnect fetch or persistence.
 * Authentication changes must reset the entire QueryClient before any
 * future credentialed data hooks are permitted.
 */
export function PreviewQueryProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [client] = useState(() => new QueryClient({
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
  }));
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
