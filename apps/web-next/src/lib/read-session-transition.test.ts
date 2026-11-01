import { describe, expect, it } from 'vitest';
import { createStagedConsoleReader, type BrowserFetch } from './staged-console-read';
import { createConsoleQueryClient, clearConsoleSessionState } from './console-query-policy';

const origin = 'https://console.lanveriq.internal';
const device = {
  device_id:'00000000-0000-7000-8000-000000000001',
  group_id:'00000000-0000-7000-8000-000000000100',
  label:'合成测试终端', group_name:'授权测试组',
  enrollment_state:'active', heartbeat_received_at:null,
  queue_count:null,
};
const json = (value: unknown) => new Response(JSON.stringify(value), {
  status:200,headers:{
    'content-type':'application/json',
    'cache-control':'private, no-store',
  },
});

/**
 * This is an entirely in-memory integration test: no browser, no
 * credentials, no real device and no network. A real authenticated
 * browser/session lifecycle must pass separately before runtime mount.
 */
describe('NXT-03B test-only Query cache and staged transport transition', () => {
  it('empties previous authorized device data after a 401 in the next request', async () => {
    const responses = [
      json({ items:[device], next_cursor:null }),
      new Response(null,{status:401}),
    ];
    let requests = 0;
    const fakeFetch: BrowserFetch = async () => {
      requests++;
      const response = responses.shift();
      if (!response) throw new Error('unexpected network request');
      return response;
    };
    const reader = createStagedConsoleReader(origin,origin,fakeFetch);
    const queryClient = createConsoleQueryClient();
    const queryKey = ['console','devices','adminA'];
    const initial = await queryClient.fetchQuery({
      queryKey, queryFn:()=>reader.list(), gcTime:60_000,
    });
    expect(initial).toMatchObject({kind:'ready',rows:[{
      id:device.device_id,
      label:device.label,
      connectivity:'unknown',
      queue:null,
    }]});
    expect(queryClient.getQueryData(queryKey)).toEqual(initial);
    const after = await reader.list();
    expect(after).toEqual({kind:'signed_out'});
    await clearConsoleSessionState(queryClient);
    expect(queryClient.getQueryData(queryKey)).toBeUndefined();
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
    expect(requests).toBe(2);
  });

  it.each([403,429,503] as const)(
    'never retains prior device rows after %i on the scoped request',
    async (status) => {
      const responses = [
        json({ items:[device], next_cursor:null }),
        new Response(null,{status}),
      ];
      const fetcher: BrowserFetch = async () => {
        const response = responses.shift();
        if (!response) throw Error('unexpected request');
        return response;
      };
      const reader = createStagedConsoleReader(origin,origin,fetcher);
      const client = createConsoleQueryClient();
      const key = ['console','devices','adminA'];
      const first = await client.fetchQuery({
        queryKey:key,queryFn:()=>reader.list(),gcTime:60_000,
      });
      expect(first.kind).toBe('ready');
      const result = await reader.list();
      expect(result.kind).toBe(status===403?'forbidden':status===429?'throttled':'unavailable');
      await clearConsoleSessionState(client);
      expect(client.getQueryData(key)).toBeUndefined();
    },
  );
});
