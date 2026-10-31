package com.lanveriq.server.devices;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Console-only, opt-in V0.1 operational metadata; not an employee-activity feed. */
@RestController
@SecurityRequirement(name = "consoleSession")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {
        "lanveriq.console.auth.enabled",
        "lanveriq.device-directory.enabled",
        "lanveriq.monitoring.overview-http-enabled"
}, havingValue = "true")
public class MonitoringOverviewRestController {
    private final MonitoringOverviewService service;

    MonitoringOverviewRestController(MonitoringOverviewService service) {
        this.service = service;
    }

    @Operation(summary = "Summarize authorized endpoint connectivity states",
            description = "Server-derived scoped totals; not user activity or recording coverage")
    @ApiResponse(responseCode = "200", description = "Current authorized overview")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Invalid console Host or Origin")
    @GetMapping(path = "/api/v1/monitoring/overview",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MonitoringOverview> overview() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.overview());
    }
}
