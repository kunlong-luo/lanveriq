/**
 * NXT-03B build-only bridge: Java 25 + Springdoc -> Orval-generated
 * Fetch/React Query hooks, using the ACTUAL Next.js package dependency
 * graph. Not imported into any runtime page, component, Route Handler,
 * Server Action or deployed server entrypoint.
 *
 * These values are never invoked. A type-level regression must fail CI
 * rather than silently substituting handwritten DTOs.
 */
import {
  csrf, consoleLogin, consoleLogout, current, list, find, overview,
  getCsrfUrl, getConsoleLoginUrl, getConsoleLogoutUrl,
} from '../.generated-contract/lanveriq';
import type {
  ConsoleCsrfToken, ConsoleLoginBody, ConsoleSessionInfo,
  DeviceHealthSummary, ScopedDeviceDirectoryPage, ScopedMonitoringOverview,
} from '../.generated-contract/model';
import type { DirectoryState } from '../src/components/device-directory';
import { validateScopedDirectoryPage } from '../src/lib/console-device-projection';

export type NextGeneratedClient = {
  csrf: typeof csrf;
  login: typeof consoleLogin;
  logout: typeof consoleLogout;
  session: typeof current;
  list: typeof list;
  detail: typeof find;
  overview: typeof overview;
  csrfUrl: typeof getCsrfUrl;
  loginUrl: typeof getConsoleLoginUrl;
  logoutUrl: typeof getConsoleLogoutUrl;
  csrfResponse: ConsoleCsrfToken;
  credentials: ConsoleLoginBody;
  currentSession: ConsoleSessionInfo;
  device: DeviceHealthSummary;
  devicePage: ScopedDeviceDirectoryPage;
  monitoringCounts: ScopedMonitoringOverview;
};

export const generatedModelToNextDisplay:
  (value: ScopedDeviceDirectoryPage) => DirectoryState = validateScopedDirectoryPage;

type AllRequired<T, K extends keyof T> =
  false extends { [P in K]: {} extends Pick<T, P> ? false : true }[K]
    ? never : true;
type Optional<T, K extends keyof T> =
  {} extends Pick<T, K> ? true : never;

export const loginRequiresPassword: AllRequired<ConsoleLoginBody, 'password' | 'username'> = true;
export const csrfIsRequired: AllRequired<ConsoleCsrfToken, 'csrfToken'> = true;
export const sessionIsRequired: AllRequired<ConsoleSessionInfo, 'login'> = true;
export const identityIsRequired: AllRequired<DeviceHealthSummary,
  'device_id' | 'group_id' | 'group_name' | 'label' | 'enrollment_state'> = true;
export const pageItemsAreRequired: AllRequired<ScopedDeviceDirectoryPage, 'items'> = true;
export const heartbeatMayBeAbsent: Optional<DeviceHealthSummary, 'heartbeat_received_at'> = true;
export const cursorMayBeAbsent: Optional<ScopedDeviceDirectoryPage, 'next_cursor'> = true;
export const monitoringCountsAreRequired: AllRequired<ScopedMonitoringOverview,
  'as_of' | 'total' | 'online' | 'stale' | 'offline' | 'revoked' | 'pending' | 'retired'> = true;
