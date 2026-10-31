package com.lanveriq.server.devices;

import java.util.UUID;

/** A single scoped database aggregation, never a sum of client-visible pages. */
interface MonitoringOverviewRepository {
    MonitoringOverview aggregate(UUID authenticatedAdminId);
}
