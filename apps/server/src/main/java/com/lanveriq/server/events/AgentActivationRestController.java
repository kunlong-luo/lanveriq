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
 * Batch A2: optional and independently gated activation of an already
 * admin-reviewed, registered P-256 client leaf. The Agent host mTLS assertion
 * is validated before any repository operation; JPA locks the exact pending
 * credential and refuses revoked, expired, foreign and unreviewed identities.
 *
 * BOTH ingress and activation flags must be deliberately enabled in a
 * separately approved isolated test. This class never signs certificates,
 * handles CA keys or mounts an enrollment/console route.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = {"lanveriq.agent.ingress.assertions-enabled",
                "lanveriq.agent.activation.http-enabled"},
        havingValue = "true")
final class AgentActivationRestController {
    private final AgentIngressAssertionPolicy ingress;
    private final DeviceActivationService activation;

    AgentActivationRestController(AgentIngressAssertionPolicy ingress,
                                  DeviceActivationService activation) {
        this.ingress = Objects.requireNonNull(ingress);
        this.activation = Objects.requireNonNull(activation);
    }

    @PostMapping(path = "/api/v1/devices/{deviceId}/activate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<DeviceActivationReceipt> accept(
            HttpServletRequest request, @PathVariable UUID deviceId,
            @RequestBody DeviceActivationRequest body) {
        VerifiedDeviceCertificate peer = ingress.resolve(request, deviceId, "activate");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(activation.activate(peer, deviceId, body));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Void> untrustedIngress(SecurityException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(DeviceActivationRejection.class)
    ResponseEntity<Void> deniedActivation(DeviceActivationRejection error) {
        HttpStatus status = error.reason() == DeviceActivationRejection.Reason.INVALID_REQUEST
                ? HttpStatus.BAD_REQUEST : HttpStatus.FORBIDDEN;
        // No distinction between non-existent, foreign, expired or revoked
        // certificate/device credentials can escape this route.
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
    }
}
