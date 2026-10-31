package com.lanveriq.server.devices;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staged console-only read path. Three independent explicit switches are
 * required. Spring Security has a separate matching chain; it rejects
 * unauthenticated sessions and non-console Host/Origin even after opt-in.
 *
 * NOT permission to expose a production endpoint before TLS/PG/manual gates.
 */
@RestController
@SecurityScheme(name = "consoleSession", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE, paramName = "__Host-lanveriq-session")
@SecurityRequirement(name = "consoleSession")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = {"lanveriq.console.auth.enabled",
                "lanveriq.device-directory.enabled",
                "lanveriq.device-directory.http-enabled"},
        havingValue = "true")
public class DeviceDirectoryRestController {
    private final DeviceDirectoryService directory;

    DeviceDirectoryRestController(DeviceDirectoryService directory) {
        this.directory = directory;
    }

    @Operation(summary = "List current group-authorized device health summaries",
            description = "Bounded UUID keyset pagination; no network identifiers or raw events")
    @ApiResponse(responseCode = "200", description = "Scoped device page")
    @ApiResponse(responseCode = "401", description = "Authentication required or revoked session")
    @ApiResponse(responseCode = "403", description = "Invalid console origin/host")
    @GetMapping(path = "/api/v1/devices", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<DeviceDirectoryRestResponse.Page> list(
            @Parameter(description = "Last returned device ID, for keyset pagination")
            @RequestParam(name = "cursor", required = false) UUID cursor,
            @Parameter(description = "Maximum number of results, 1 to 100")
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(DeviceDirectoryRestResponse.Page.from(directory.list(cursor, limit)));
    }

    @Operation(summary = "Read one group-authorized device health summary",
            description = "Unknown and out-of-scope device identifiers are indistinguishable")
    @ApiResponse(responseCode = "200", description = "Scoped health summary")
    @ApiResponse(responseCode = "404", description = "No visible device with this identifier")
    @GetMapping(path = "/api/v1/devices/{deviceId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<DeviceDirectoryRestResponse.DeviceItem> find(
            @PathVariable("deviceId") UUID deviceId) {
        return directory.find(deviceId)
                .map(DeviceDirectoryRestResponse.DeviceItem::from)
                .map(value -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(value))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .cacheControl(CacheControl.noStore()).build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidQuery() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
    }
}
