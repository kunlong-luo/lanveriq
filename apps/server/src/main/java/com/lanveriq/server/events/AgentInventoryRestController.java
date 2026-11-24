package com.lanveriq.server.events;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory ingress is separately gated from activation and heartbeat.
 * The ephemeral CI test runs real Caddy/Tomcat TLS1.3, current client
 * certificate and the existing JPA credential guard before persistence.
 * The installed Agent has no background sender and this route is OFF by default.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {
        "lanveriq.agent.ingress.assertions-enabled",
        "lanveriq.agent.inventory.http-enabled"}, havingValue = "true")
final class AgentInventoryRestController {
    private final AgentIngressAssertionPolicy ingress;
    private final DeviceInventoryService inventory;

    AgentInventoryRestController(AgentIngressAssertionPolicy ingress,
                                 DeviceInventoryService inventory) {
        this.ingress = Objects.requireNonNull(ingress);
        this.inventory = Objects.requireNonNull(inventory);
    }

    @PostMapping(path = "/api/v1/devices/{deviceId}/inventory",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<DeviceInventoryReceipt> accept(
            HttpServletRequest request, @PathVariable UUID deviceId,
            @RequestBody DeviceInventorySnapshot body) {
        VerifiedDeviceCertificate peer = ingress.resolve(request, deviceId, "inventory");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(inventory.ingest(peer, deviceId, body));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Void> untrustedIngress(SecurityException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(DeviceInventoryRejection.class)
    ResponseEntity<Void> refusedInventory(DeviceInventoryRejection rejected) {
        HttpStatus status = switch (rejected.reason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case REQUEST_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case REVOKED_DEVICE -> HttpStatus.FORBIDDEN;
            case REVISION_CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
    }
}
