import { describe, expect, it } from 'vitest';
import { devices, filterDevices, stateCounts } from './demo';
describe('synthetic device preview',()=>{
  it('separates online from stale/offline/revoked',()=>expect(stateCounts(devices)).toEqual({online:2,stale:1,offline:1,revoked:1}));
  it('filters only local sample rows',()=>expect(filterDevices(devices,'windows','研发组').map(x=>x.name)).toEqual(['演示设备 A']));
  it('handles an empty search safely',()=>expect(filterDevices(devices,'not-real','全部分组')).toEqual([]));
  it('uses obvious placeholder device identifiers',()=>expect(devices.every(x=>x.id.startsWith('00000000-0000-7000-8000-'))).toBe(true));
});
