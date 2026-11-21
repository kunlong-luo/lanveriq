import { describe, expect, it } from 'vitest';
import { isCanonicalUuid } from './uuid';

describe('NXT-02 safe navigation identifier format', () => {
  it.each([
    '00000000-0000-7000-8000-000000000001',
    '019a47dd-a643-744d-bfa0-c6b7ce6de912',
    '019A47DD-A643-744D-BFA0-C6B7CE6DE912',
  ])('accepts five-segment UUID %s without claiming authorization', value => {
    expect(isCanonicalUuid(value)).toBe(true);
  });
  it.each([
    '00000000-0000-7000-000000000001', // missing the fourth segment
    'not-a-uuid', '../auth/logout', '%2fapi%2fv1%2f', '',
    'https://evil.example/device', '00000000-0000-7000-8000-000000000001?x=1',
    null, undefined, 42,
  ])('rejects malformed navigation input %s', value => {
    expect(isCanonicalUuid(value)).toBe(false);
  });
});
