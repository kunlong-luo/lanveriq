package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * L1 contract negatives only. These use mock mTLS evidence and therefore
 * explicitly DO NOT establish real TLS, Caddy, DB or Agent device acceptance.
 */
class AgentHeartbeatRestControllerTest {

    private final UUID device = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final VerifiedDeviceCertificate identity =
            new VerifiedDeviceCertificate(device, "a".repeat(64));
    private final AgentIngressAssertionPolicy ingress =
            mock(AgentIngressAssertionPolicy.class);
    private final DeviceHeartbeatService service = mock(DeviceHeartbeatService.class);
    private final AgentHeartbeatRestController controller =
            new AgentHeartbeatRestController(ingress, service);

    private String path() {
        return "/api/v1/devices/" + device + "/heartbeats";
    }

    private String json() {
        return """
                {"schema_version":1,"device_id":"%s",
                 "sent_at":"2026-11-08T04:25:45Z","agent_version":"0.1.0",
                 "policy_version":1,"status":"running",
                 "capabilities":{"hardware_inventory":"not_collected"},
                 "queue_count":0,"queue_bytes":0,
                 "oldest_pending_at":null,"last_successful_upload_at":null,
                 "dropped_count":0,"integrity_degraded":false,
                 "collection_errors":[],"request_id":"%s"}
                """.formatted(device, requestId);
    }

    @Test
    void controllerAndStatelessChainAreBothDisabledUnlessTwoExplicitFlagsAreTrue() {
        for (Class<?> component : new Class<?>[]{
                AgentHeartbeatRestController.class,
                AgentHeartbeatHttpSecurityConfiguration.class}) {
            var property = component.getAnnotation(ConditionalOnProperty.class);
            assertTrue(property != null);
            assertTrue(Arrays.asList(property.name()).contains(
                    "lanveriq.agent.ingress.assertions-enabled"));
            assertTrue(Arrays.asList(property.name()).contains(
                    "lanveriq.agent.heartbeat.http-enabled"));
            assertTrue("true".equals(property.havingValue()));
            assertTrue(!property.matchIfMissing());
        }
    }

    @Test
    void onlyPinnedProxyIdentityThenDatabaseCommitCanReturnNoStoreReceipt()
            throws Exception {
        when(ingress.resolve(any(HttpServletRequest.class), eq(device), eq("heartbeats")))
                .thenReturn(identity);
        var receipt = new DeviceHeartbeatReceipt(Instant.parse("2026-11-08T04:25:46Z"),
                requestId);
        when(service.ingest(eq(identity), eq(device), any(DeviceHeartbeat.class)))
                .thenReturn(receipt);
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(json()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.request_id").value(requestId.toString()));
        verify(ingress).resolve(any(HttpServletRequest.class), eq(device), eq("heartbeats"));
        verify(service).ingest(eq(identity), eq(device), any(DeviceHeartbeat.class));
    }

    @Test
    void forgedOrMissingVerifiedPeerIsDeniedBeforeAnyDatabaseOperation()
            throws Exception {
        when(ingress.resolve(any(HttpServletRequest.class), eq(device), eq("heartbeats")))
                .thenThrow(new SecurityException("untrusted proxy"));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(json()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service, never()).ingest(
                any(VerifiedDeviceCertificate.class), any(UUID.class),
                any(DeviceHeartbeat.class));
    }

    @Test
    void acceptedLedgerLookupRequiresPinnedProxyAndDoesNotExposeUnknownUuid()
            throws Exception {
        when(ingress.resolveHeartbeatLookup(any(HttpServletRequest.class),
                eq(device), eq(requestId))).thenReturn(identity);
        var valid = new DeviceHeartbeatLookupReceipt(1, device, requestId,
                true, Instant.parse("2026-11-08T04:25:46Z"));
        when(service.lookupAccepted(eq(identity), eq(device), eq(requestId)))
                .thenReturn(java.util.Optional.of(valid));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        String target = path() + "/requests/" + requestId;
        mvc.perform(get(target))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.request_id").value(requestId.toString()))
                .andExpect(jsonPath("$.device_id").value(device.toString()))
                .andExpect(jsonPath("$.committed").value(true));
        verify(ingress).resolveHeartbeatLookup(any(HttpServletRequest.class),
                eq(device), eq(requestId));
        verify(service).lookupAccepted(eq(identity), eq(device), eq(requestId));

        org.mockito.Mockito.reset(service);
        when(service.lookupAccepted(eq(identity), eq(device), eq(requestId)))
                .thenReturn(java.util.Optional.empty());
        mvc.perform(get(target))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsString().isEmpty()));
    }

    @Test
    void untrustedLookupDoesNotTouchDatabase() throws Exception {
        when(ingress.resolveHeartbeatLookup(any(HttpServletRequest.class),
                eq(device), eq(requestId))).thenThrow(new SecurityException("untrusted"));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get(path() + "/requests/" + requestId))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service, never()).lookupAccepted(any(), any(), any());
    }

    @Test
    void invalidOversizedAndRevokedHeartbeatsNeverDiscloseDeviceExistence()
            throws Exception {
        when(ingress.resolve(any(HttpServletRequest.class), eq(device), eq("heartbeats")))
                .thenReturn(identity);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (var reason : DeviceHeartbeatRejection.Reason.values()) {
            org.mockito.Mockito.reset(service);
            when(service.ingest(eq(identity), eq(device), any(DeviceHeartbeat.class)))
                    .thenThrow(new DeviceHeartbeatRejection(reason));
            int expected = switch (reason) {
                case INVALID_SCHEMA -> 400;
                case REQUEST_TOO_LARGE -> 413;
                case REVOKED_DEVICE -> 403;
                case REPLAYED_REQUEST -> 409;
            };
            mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(json()))
                    .andExpect(status().is(expected))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(result -> assertTrue(
                            result.getResponse().getContentAsString().isEmpty()));
        }
    }
}
