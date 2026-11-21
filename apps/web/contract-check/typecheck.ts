/**
 * NXT-03 TYPE-ONLY contract bridge. Input is the Java-generated Springdoc
 * OpenAPI -> Orval output, copied by the server CI after genuine generation.
 * This file is NOT imported by the Next.js runtime, contains NO network
 * call, and does not create routes or an authenticated server-side fetch.
 */
import type {
  DeviceHealthSummary,
  ScopedDeviceDirectoryPage,
  ScopedMonitoringOverview,
  ConsoleSessionInfo,
  ConsoleCsrfToken,
} from '../.generated-contract/model';
import { validateScopedDirectoryPage } from '../src/lib/console-device-projection';
import type { DirectoryState } from '../src/components/device-directory';

type AllRequired<T, K extends keyof T> =
  false extends { [P in K]: {} extends Pick<T, P> ? false : true }[K]
    ? never : true;
type Optional<T, K extends keyof T> =
  {} extends Pick<T, K> ? true : never;

export const itemIdentityRequired: AllRequired<DeviceHealthSummary,
  'device_id' | 'group_id' | 'label' | 'group_name' | 'enrollment_state'> = true;
export const itemsRequired: AllRequired<ScopedDeviceDirectoryPage,'items'> = true;
export const cursorOptional: Optional<ScopedDeviceDirectoryPage,'next_cursor'> = true;
export const heartbeatOptional: Optional<DeviceHealthSummary,'heartbeat_received_at'> = true;
export const monitoringRequired: AllRequired<ScopedMonitoringOverview,
  'as_of' | 'total' | 'online' | 'stale' | 'offline' | 'revoked' | 'pending' | 'retired'> = true;
export const sessionRequired: AllRequired<ConsoleSessionInfo,'login'> = true;
export const csrfRequired: AllRequired<ConsoleCsrfToken,'csrfToken'> = true;

export const generatedPageAccepted:
  (page: ScopedDeviceDirectoryPage) => DirectoryState =
  validateScopedDirectoryPage;
export const generatedItemIdentity: (row: DeviceHealthSummary) => string =
  row => row.device_id;
