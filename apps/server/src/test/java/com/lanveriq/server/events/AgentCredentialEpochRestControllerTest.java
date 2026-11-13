package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Synthetic MockMvc is not a substitute for actual double mTLS and PG18. */
class AgentCredentialEpochRestControllerTest {
    private final UUID device = UUID.randomUUID();
    private final UUID epoch = UUID.randomUUID();
    private final VerifiedDeviceCertificate peer =
            new VerifiedDeviceCertificate(device, "a".repeat(64));
    private final AgentIngressAssertionPolicy ingress = mock(AgentIngressAssertionPolicy.class);
    private final DeviceHeartbeatService service = mock(DeviceHeartbeatService.class);
    private final AgentCredentialEpochRestController controller =
            new AgentCredentialEpochRestController(ingress, service);

    private String url() {
        return "/api/v1/devices/" + device + "/heartbeats/credential-epoch";
    }

    @Test
    void independentThirdFlagMustExplicitlyMountReadOnlyRoute() {
        var a = AgentCredentialEpochRestController.class.getAnnotation(ConditionalOnProperty.class);
        assertTrue(a != null && "true".equals(a.havingValue()) && !a.matchIfMissing());
        assertTrue(Arrays.asList(a.name()).containsAll(Arrays.asList(
                "lanveriq.agent.ingress.assertions-enabled",
                "lanveriq.agent.heartbeat.http-enabled",
                "lanveriq.agent.credential-epoch.http-enabled")));
    }

    @Test
    void pinnedProxyAndDatabaseLockReturnStrictNoStoreEpoch() throws Exception {
        when(ingress.resolveCurrentCredentialEpoch(any(HttpServletRequest.class), eq(device)))
                .thenReturn(peer);
        when(service.inspectCurrentCredentialEpoch(peer, device))
                .thenReturn(new DeviceCredentialEpochReceipt(1, device, epoch,
                        peer.sha256Fingerprint(), Instant.parse("2026-11-13T09:00:00Z")));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get(url()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.device_id").value(device.toString()))
                .andExpect(jsonPath("$.credential_epoch_id").value(epoch.toString()))
                .andExpect(jsonPath("$.client_cert_sha256_fingerprint")
                        .value(peer.sha256Fingerprint()));
        verify(service).inspectCurrentCredentialEpoch(peer, device);
    }

    @Test
    void fakeUpstreamPeerIsDeniedBeforeAnyDatabaseAccess() throws Exception {
        when(ingress.resolveCurrentCredentialEpoch(any(HttpServletRequest.class), eq(device)))
                .thenThrow(new SecurityException("untrusted"));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get(url()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service, never()).inspectCurrentCredentialEpoch(any(), any());
    }

    @Test
    void revokedCertificateNeverReturnsCredentialEpoch() throws Exception {
        when(ingress.resolveCurrentCredentialEpoch(any(HttpServletRequest.class), eq(device)))
                .thenReturn(peer);
        when(service.inspectCurrentCredentialEpoch(peer, device))
                .thenThrow(new DeviceHeartbeatRejection(
                        DeviceHeartbeatRejection.Reason.REVOKED_DEVICE));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get(url()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
