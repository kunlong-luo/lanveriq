package com.lanveriq.server.events;

import jakarta.servlet.http.HttpServletRequest;
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
 * Batch A: strictly opt-in Agent heartbeat ingress.
 *
 * The Caddy-upstream mTLS peer certificate is pinned by the parser bean's
 * startup guard. It must be independently verified by actual TLS acceptance
 * before an operator may enable these flags outside a disposable sandbox.
 * The payload device_id/fingerprint header is NOT independent identity.
 * JPA re-locks the current device and active credential before recording.
 *
 * Both `lanveriq.agent.ingress.assertions-enabled` and
 * `lanveriq.agent.heartbeat.http-enabled` must be explicitly true; default
 * production configuration never mounts the route.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = {"lanveriq.agent.ingress.assertions-enabled",
                "lanveriq.agent.heartbeat.http-enabled"},
        havingValue = "true")
final class AgentHeartbeatRestController {
    private final AgentIngressAssertionPolicy ingress;
    private final DeviceHeartbeatService heartbeats;

    AgentHeartbeatRestController(AgentIngressAssertionPolicy ingress,
                                DeviceHeartbeatService heartbeats) {
        this.ingress = java.util.Objects.requireNonNull(ingress);
        this.heartbeats = java.util.Objects.requireNonNull(heartbeats);
    }

    @PostMapping(path = "/api/v1/devices/{deviceId}/heartbeats",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<DeviceHeartbeatReceipt> accept(
            HttpServletRequest request,
            @PathVariable UUID deviceId,
            @RequestBody DeviceHeartbeat body) {
        // A forged header, non-TLS request, wrong Host, direct Node origin or
        // missing pinned Caddy upstream client certificate is denied BEFORE DB.
        VerifiedDeviceCertificate peer = ingress.resolve(request, deviceId, "heartbeats");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(heartbeats.ingest(peer, deviceId, body));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Void> untrustedIngress(SecurityException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(DeviceHeartbeatRejection.class)
    ResponseEntity<Void> rejectedHeartbeat(DeviceHeartbeatRejection error) {
        HttpStatus status = switch (error.reason()) {
            case INVALID_SCHEMA -> HttpStatus.BAD_REQUEST;
            case REQUEST_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case REVOKED_DEVICE -> HttpStatus.FORBIDDEN;
            case REPLAYED_REQUEST -> HttpStatus.CONFLICT;
        };
        // Do not disclose whether a device does not exist, is revoked, or
        // belongs to another group in any error response.
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
    }
}
