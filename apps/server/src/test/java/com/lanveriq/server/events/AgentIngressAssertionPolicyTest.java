package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AgentIngressAssertionPolicyTest {
    private static final String HOST = "agent.lanveriq.internal";
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private final UUID deviceId = UUID.randomUUID();
    private final AgentIngressAssertionPolicy policy =
            new AgentIngressAssertionPolicy(HOST);

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest(
                "POST", "/api/v1/devices/" + deviceId + "/activate");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Host", HOST);
        request.addHeader(AgentIngressAssertionPolicy.FINGERPRINT_HEADER, FINGERPRINT);
        return request;
    }

    @Test
    void syntheticIsolatedProxyAssertionProvidesBoundIdentityForFurtherDatabaseChecks() {
        var evidence = policy.resolve(request(), deviceId, "activate");
        assertEquals(deviceId, evidence.deviceId());
        assertEquals(FINGERPRINT, evidence.sha256Fingerprint());
    }

    @Test
    void wrongOrDirectPeerAndHostOrMethodCannotProvideIdentity() {
        for (String remote : new String[]{"192.168.1.20", "172.17.0.1", "::1"}) {
            assertDenied(request -> request.setRemoteAddr(remote));
        }
        assertDenied(request -> request.setMethod("GET"));
        assertDenied(request -> request.setRequestURI(
                "/api/v1/devices/" + UUID.randomUUID() + "/activate"));
        assertDenied(request -> request.setRequestURI(
                "/api/v1/devices/" + deviceId + "/event-batches"));
        assertDenied(request -> request.setQueryString("credential=forged"));
        assertDenied(request -> request.removeHeader("Host"));
        assertDenied(request -> request.addHeader("Host", "console.lanveriq.internal"));
        assertDenied(request -> {
            request.removeHeader("Host");
            request.addHeader("Host", "enroll.lanveriq.internal");
        });
        assertDenied(request -> request.addHeader("Host", HOST));
    }

    @Test
    void clientSuppliedIdentityAliasesOrMultipleValuesAreDenied() {
        assertDenied(request -> request.addHeader(
                AgentIngressAssertionPolicy.FINGERPRINT_HEADER, "b".repeat(64)));
        assertDenied(request -> request.addHeader(
                "X-Lanveriq-Verified-Client-Certificate", "forged"));
        assertDenied(request -> request.addHeader(
                "X_Lanveriq_Verified_Client_Fingerprint", FINGERPRINT));
        assertDenied(request -> request.addHeader("X-Forwarded-Client-Cert", "forged"));
        assertDenied(request -> request.addHeader("SSL-Client-Cert", "forged"));
        assertDenied(request -> request.addHeader("X-Lanveriq-Verified-Ingress", "agent"));
    }

    @Test
    void malformedOrEmptyCertificateHashCannotBeTrusted() {
        assertDenied(request -> request.removeHeader(
                AgentIngressAssertionPolicy.FINGERPRINT_HEADER));
        assertDenied(request -> {
            request.removeHeader(AgentIngressAssertionPolicy.FINGERPRINT_HEADER);
            request.addHeader(AgentIngressAssertionPolicy.FINGERPRINT_HEADER, EMPTY_SHA256);
        });
        assertDenied(request -> {
            request.removeHeader(AgentIngressAssertionPolicy.FINGERPRINT_HEADER);
            request.addHeader(AgentIngressAssertionPolicy.FINGERPRINT_HEADER, "A".repeat(64));
        });
        assertDenied(request -> {
            request.removeHeader(AgentIngressAssertionPolicy.FINGERPRINT_HEADER);
            request.addHeader(AgentIngressAssertionPolicy.FINGERPRINT_HEADER, "a".repeat(32) + ",b");
        });
    }

    @Test
    void noUnrecognizedRouteOrBodyIdentityIsAccepted() {
        var other = UUID.randomUUID();
        assertThrows(SecurityException.class,
                () -> policy.resolve(request(), other, "activate"));
        assertThrows(SecurityException.class,
                () -> policy.resolve(request(), deviceId, "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentIngressAssertionPolicy("https://agent.lanveriq.internal"));
    }

    private void assertDenied(Consumer<MockHttpServletRequest> mutation) {
        var request = request();
        mutation.accept(request);
        assertThrows(SecurityException.class,
                () -> policy.resolve(request, deviceId, "activate"));
    }
}
