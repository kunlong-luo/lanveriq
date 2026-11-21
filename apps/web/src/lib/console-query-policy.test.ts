import { describe, expect, it } from 'vitest';
import { createConsoleQueryClient, clearConsoleSessionState } from './console-query-policy';

describe('NXT-03B session-scoped TanStack Query policy (no network)', () => {
  it('disables implicit refetching, retries and stale-cache retention', () => {
    const client = createConsoleQueryClient();
    expect(client.getDefaultOptions().queries).toMatchObject({
      retry: false,
      refetchOnMount: false,
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      staleTime: 0,
      gcTime: 0,
    });
    expect(client.getDefaultOptions().mutations).toMatchObject({ retry: false });
    client.clear();
  });

  it('drops all device and session data after a principal/session change', async () => {
    const client = createConsoleQueryClient();
    client.setQueryData(['console','devices','adminA'], [{label:'sensitive-only-in-test'}]);
    client.setQueryData(['console','monitoring'], {total:1});
    expect(client.getQueryCache().getAll().length).toBe(2);
    await clearConsoleSessionState(client);
    expect(client.getQueryCache().getAll()).toHaveLength(0);
    expect(client.getQueryData(['console','devices','adminA'])).toBeUndefined();
    expect(client.getQueryData(['console','monitoring'])).toBeUndefined();
    client.clear();
  });
});
