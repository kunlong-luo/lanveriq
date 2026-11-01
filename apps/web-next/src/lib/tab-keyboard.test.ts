import { describe, expect, it } from 'vitest';
import { nextTabIndex } from './tab-keyboard';

describe('NXT-02 WAI-ARIA detail tab keyboard navigation', () => {
  it('cycles in both directions and selects first/last with Home/End', () => {
    expect(nextTabIndex('ArrowRight',0,3)).toBe(1);
    expect(nextTabIndex('ArrowRight',2,3)).toBe(0);
    expect(nextTabIndex('ArrowLeft',0,3)).toBe(2);
    expect(nextTabIndex('ArrowLeft',2,3)).toBe(1);
    expect(nextTabIndex('Home',2,3)).toBe(0);
    expect(nextTabIndex('End',0,3)).toBe(2);
  });
  it('does not intercept native activation keys or invalid indexes', () => {
    expect(nextTabIndex('Tab',0,3)).toBeNull();
    expect(nextTabIndex('Enter',0,3)).toBeNull();
    expect(nextTabIndex(' ',0,3)).toBeNull();
    expect(nextTabIndex('ArrowRight',-1,3)).toBeNull();
    expect(nextTabIndex('ArrowRight',0,0)).toBeNull();
  });
});
