package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Strictly local MockMvc/Mockito tests; no Caddy, actual PKI, Docker or PG18
 * handshake is claimed by these assertions.
 */
class AgentActivationRestControllerTest {
    private final UUID deviceId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final VerifiedDeviceCertificate peer =
            new VerifiedDeviceCertificate(deviceId, "a".repeat(64));
    private final AgentIngressAssertionPolicy ingress =
            mock(AgentIngressAssertionPolicy.class);
    private final DeviceActivationService activation =
            mock(DeviceActivationService.class);
    private final AgentActivationRestController controller =
            new AgentActivationRestController(ingress, activation);

    private String url() {
        return "/api/v1/devices/" + deviceId + "/activate";
    }

    private String json() {
        return """
                {"schema_version":1,"device_id":"%s",
                 "agent_version":"0.1.0","request_id":"%s"}
                """.formatted(deviceId, requestId);
    }

    @Test
    void controllerAndItsStatelessChainRequireBothExplicitOptInFlags() {
        for (Class<?> component : new Class<?>[]{
                AgentActivationRestController.class,
                AgentActivationHttpSecurityConfiguration.class}) {
            var annotation = component.getAnnotation(ConditionalOnProperty.class);
            assertTrue(annotation != null);
            assertTrue(Arrays.asList(annotation.name())
                    .contains("lanveriq.agent.ingress.assertions-enabled"));
            assertTrue(Arrays.asList(annotation.name())
                    .contains("lanveriq.agent.activation.http-enabled"));
            assertEquals("true", annotation.havingValue());
            assertTrue(!annotation.matchIfMissing());
        }
    }

    @Test
    void verifiedUpstreamIdentityAndRepositorySuccessReturnNoStoreExactReceipt()
            throws Exception {
        when(ingress.resolve(any(HttpServletRequest.class), eq(deviceId), eq("activate")))
                .thenReturn(peer);
        var receipt = new DeviceActivationReceipt(1, deviceId, "active",
                Instant.parse("2026-11-09T12:00:00Z"), requestId);
        when(activation.activate(eq(peer), eq(deviceId), any(DeviceActivationRequest.class)))
                .thenReturn(receipt);

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post(url()).contentType(MediaType.APPLICATION_JSON).content(json()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.request_id").value(requestId.toString()))
                .andExpect(jsonPath("$.enrollment_state").value("active"));
        var argument = ArgumentCaptor.forClass(DeviceActivationRequest.class);
        verify(activation).activate(eq(peer), eq(deviceId), argument.capture());
        assertEquals(requestId, argument.getValue().requestId());
    }

    @Test
    void invalidOrForgedUpstreamAssertionCannotReachAnyDatabaseOperation()
            throws Exception {
        when(ingress.resolve(any(HttpServletRequest.class), eq(deviceId), eq("activate")))
                .thenThrow(new SecurityException("untrusted proxy"));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post(url()).contentType(MediaType.APPLICATION_JSON).content(json()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(result ->
                        assertTrue(result.getResponse().getContentAsString().isEmpty()));
        verify(activation, never()).activate(any(), any(), any());
    }

    @Test
    void invalidRequestAndDeniedPendingCredentialNeverRevealDeviceExistence()
            throws Exception {
        when(ingress.resolve(any(HttpServletRequest.class), eq(deviceId), eq("activate")))
                .thenReturn(peer);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (var reason : DeviceActivationRejection.Reason.values()) {
            org.mockito.Mockito.reset(activation);
            when(activation.activate(eq(peer), eq(deviceId), any(DeviceActivationRequest.class)))
                    .thenThrow(new DeviceActivationRejection(reason));
            int expected = reason == DeviceActivationRejection.Reason.INVALID_REQUEST
                    ? 400 : 403;
            mvc.perform(post(url()).contentType(MediaType.APPLICATION_JSON).content(json()))
                    .andExpect(status().is(expected))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(result ->
                            assertTrue(result.getResponse().getContentAsString().isEmpty()));
        }
    }
}
