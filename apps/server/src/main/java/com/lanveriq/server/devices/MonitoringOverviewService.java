package com.lanveriq.server.devices;

import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** No HTTP exposure when the separate monitoring gate is disabled. */
@Service
@ConditionalOnProperty(prefix = "lanveriq.device-directory",
        name = "enabled", havingValue = "true")
public class MonitoringOverviewService {
    private final MonitoringOverviewRepository repository;

    MonitoringOverviewService(MonitoringOverviewRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional(readOnly = true)
    public MonitoringOverview overview() {
        return repository.aggregate(DeviceDirectoryService.requireAdmin());
    }
}
