/**
 * Shared identifier syntax check for navigation only. A syntactically valid
 * UUID is NOT proof of device existence, session state or RBAC permission.
 * Actual authorization remains a Spring Security/JPA responsibility.
 */
const canonicalUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isCanonicalUuid(input: unknown): input is string {
  return typeof input === 'string' && canonicalUuid.test(input);
}
