import { describe, expect, it } from 'vitest';
import { createStagedConsoleReader, type BrowserFetch } from './staged-console-read';

const host = 'https://console.lanveriq.internal';
const id = '00000000-0000-7000-8000-000000000001';
const page = { items: [], next_cursor: null };
const json = (data: unknown, cache = 'no-store') => new Response(JSON.stringify(data), {
  status:200, headers: { 'content-type':'application/json', 'cache-control':cache },
});
function stub(responses: Array<Response | Error>) {
  const sent: Array<{url:string;init?:RequestInit}> = [];
  const fetcher: BrowserFetch = async (input,init) => {
    sent.push({url:String(input),init});
    const response = responses.shift();
    if (!response || response instanceof Error) throw response ?? new Error('not stubbed');
    return response;
  };
  return {sent,fetcher};
}

describe('NXT-03 standalone staged browser GET transport, NOT mounted', () => {
  it('rejects noncanonical, untrusted or different browser origin before any fetch', () => {
    const {fetcher,sent} = stub([]);
    for (const browser of [
      'http://localhost:3100','https://localhost','http://console.lanveriq.internal',
      'https://other.lanveriq.internal','https://127.0.0.1',
      'https://console.lanveriq.internal/', 'https://console.lanveriq.internal:443',
      'https://CONSOLE.lanveriq.internal', 'https://console.lanveriq.internal/path',
    ]) {
      expect(() => createStagedConsoleReader(host,browser,fetcher)).toThrow();
    }
    expect(() => createStagedConsoleReader('http://console.lanveriq.internal',host,fetcher)).toThrow();
    expect(sent).toHaveLength(0);
  });

  it('calls only same-origin, no-store, credentials:same-origin bounded list GET', async () => {
    const {sent,fetcher}=stub([json(page)]);
    const client=createStagedConsoleReader(host,host,fetcher);
    await expect(client.list()).resolves.toEqual({kind:'ready',rows:[],hasMore:false,nextCursor:null});
    expect(sent[0].url).toBe(host+'/api/v1/devices?limit=50');
    expect(sent[0].init).toMatchObject({
      method:'GET',credentials:'same-origin',mode:'same-origin',cache:'no-store',
      redirect:'error',referrerPolicy:'no-referrer',
      headers:{Accept:'application/json'},
    });
    expect(sent[0].init?.body).toBeUndefined();
    expect(new Headers(sent[0].init?.headers).has('Origin')).toBe(false);
  });

  it('allows only specified read-only details, monitoring and keyset paths', async () => {
    const {sent,fetcher}=stub([json(page),json({device_id:id}),json({total:0})]);
    const client=createStagedConsoleReader(host,host,fetcher);
    await client.list({limit:2,after:id});
    await client.detail(id);
    await client.overview();
    expect(sent.map(x=>new URL(x.url).pathname)).toEqual([
      '/api/v1/devices', '/api/v1/devices/'+id, '/api/v1/monitoring/overview',
    ]);
    expect(new URL(sent[0].url).searchParams.get('cursor')).toBe(id);
    expect(new URL(sent[0].url).searchParams.get('limit')).toBe('2');
    expect(sent.every(x=>x.init?.method==='GET')).toBe(true);
  });

  it('rejects invalid cursor, limit or ID before any network', () => {
    const {fetcher,sent}=stub([]);
    const c=createStagedConsoleReader(host,host,fetcher);
    for(const after of ['../../auth/logout','%2f','https://evil.example','bad','']) {
      expect(()=>c.list({after})).toThrow();
      expect(()=>c.detail(after)).toThrow();
    }
    for(const limit of [0,101,1.1,-1,NaN,Infinity]) expect(()=>c.list({limit})).toThrow();
    expect(sent).toHaveLength(0);
  });

  it.each([
    [401,'signed_out'],[403,'forbidden'],[429,'throttled'],
    [404,'unavailable'],[503,'unavailable'],[302,'unavailable'],
  ] as const)('list status %i clears display with %s',async(status,kind)=>{
    const {fetcher}=stub([new Response(null,{status})]);
    await expect(createStagedConsoleReader(host,host,fetcher).list()).resolves.toEqual({kind});
  });

  it('treats 404 as not_found only for device detail',async()=>{
    const {fetcher}=stub([new Response(null,{status:404})]);
    await expect(createStagedConsoleReader(host,host,fetcher).detail(id))
      .resolves.toEqual({kind:'not_found'});
  });

  it('rejects cacheable, malformed, wrong-content-type, null and network responses',async()=>{
    for(const item of [
      json(page,'public, max-age=60'), json(page,'no-store-bad'),
      new Response('<html>login</html>',{status:200,
        headers:{'content-type':'text/html','cache-control':'no-store'}}),
      new Response('{bad-json',{status:200,
        headers:{'content-type':'application/json','cache-control':'no-store'}}),
      json(null),json([]),new Error('offline'),
    ]) {
      const {fetcher}=stub([item]);
      await expect(createStagedConsoleReader(host,host,fetcher).list())
        .resolves.toEqual({kind:'unavailable'});
    }
    const {fetcher}=stub([json({items:[]})]);
    await expect(createStagedConsoleReader(host,host,fetcher).list())
      .resolves.toEqual({kind:'unavailable'});
  });
});
