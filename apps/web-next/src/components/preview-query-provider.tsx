'use client';

import { useState, type ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { createConsoleQueryClient } from '../lib/console-query-policy';

/**
 * Mounting the context does NOT mount Orval hooks, fetch server data
 * or activate an administrator session. The restrictive defaults and
 * session-clearing helper are separately unit-tested.
 */
export function PreviewQueryProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [client] = useState(createConsoleQueryClient);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
