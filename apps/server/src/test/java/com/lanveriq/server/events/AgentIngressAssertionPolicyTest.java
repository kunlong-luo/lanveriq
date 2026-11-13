package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.UUID;
import org.mockito.Mockito;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AgentIngressAssertionPolicyTest {
    private static final String HOST = "agent.lanveriq.internal";
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private final UUID deviceId = UUID.randomUUID();
    private static final byte[] CADDY_CLIENT_DER =
            "synthetic-verified-upstream-proxy-client-der".getBytes(StandardCharsets.UTF_8);
    private final AgentIngressAssertionPolicy policy =
            new AgentIngressAssertionPolicy(HOST, sha256(CADDY_CLIENT_DER));

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest(
                "POST", "/api/v1/devices/" + deviceId + "/activate");
        request.setRemoteAddr("127.0.0.1");
        request.setSecure(true);
        request.setScheme("https");
        request.setAttribute("jakarta.servlet.request.X509Certificate",
                new X509Certificate[]{syntheticTlsPeer(CADDY_CLIENT_DER)});
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
    void onlyExactGetReceiptLookupRetainsFullPinnedTlsIdentityGate() {
        UUID requestId = UUID.randomUUID();
        var accepted = request();
        accepted.setMethod("GET");
        accepted.setRequestURI("/api/v1/devices/" + deviceId
                + "/heartbeats/requests/" + requestId);
        var identity = policy.resolveHeartbeatLookup(accepted, deviceId, requestId);
        assertEquals(deviceId, identity.deviceId());
        assertEquals(FINGERPRINT, identity.sha256Fingerprint());

        // HTTP/2 canonical authority may omit ordinary Host, as on POST.
        accepted.setProtocol("HTTP/2.0");
        accepted.removeHeader("Host");
        accepted.setServerName(HOST);
        assertEquals(deviceId, policy.resolveHeartbeatLookup(
                accepted, deviceId, requestId).deviceId());

        var invalid = request();
        invalid.setMethod("POST");
        invalid.setRequestURI("/api/v1/devices/" + deviceId
                + "/heartbeats/requests/" + requestId);
        assertThrows(SecurityException.class,
                () -> policy.resolveHeartbeatLookup(invalid, deviceId, requestId));
        invalid.setMethod("GET");
        assertThrows(SecurityException.class,
                () -> policy.resolveHeartbeatLookup(invalid, deviceId, UUID.randomUUID()));
        assertThrows(SecurityException.class,
                () -> policy.resolveHeartbeatLookup(invalid, deviceId, new UUID(0, 0)));
        invalid.setQueryString("state=known");
        assertThrows(SecurityException.class,
                () -> policy.resolveHeartbeatLookup(invalid, deviceId, requestId));
        invalid.setQueryString(null);
        invalid.removeAttribute("jakarta.servlet.request.X509Certificate");
        assertThrows(SecurityException.class,
                () -> policy.resolveHeartbeatLookup(invalid, deviceId, requestId));
        // A GET to the ordinary heartbeat POST must never be accepted.
        assertThrows(SecurityException.class,
                () -> policy.resolve(accepted, deviceId, "heartbeats"));
    }

    @Test
    void http2UsesCanonicalServletAuthorityWithoutAcceptingHostSpoofing() {
        var h2 = request();
        h2.setProtocol("HTTP/2.0");
        h2.removeHeader("Host");
        h2.setServerName(HOST);
        var identity = policy.resolve(h2, deviceId, "activate");
        assertEquals(deviceId, identity.deviceId());
        assertEquals(FINGERPRINT, identity.sha256Fingerprint());

        // HTTP/1.1 with no Host remains denied, and HTTP/2 cannot replace
        // the exact authority with another Caddy virtual host.
        assertDenied(request -> {
            request.setProtocol("HTTP/2.0");
            request.removeHeader("Host");
            request.setServerName("console.lanveriq.internal");
        });
        // MockHttpServletRequest derives getServerName() from its existing
        // Host header, so a spy is necessary to model a conflicting HTTP/2
        // :authority when an ordinary Host is also present.
        var conflictingAuthority = Mockito.spy(request());
        conflictingAuthority.setProtocol("HTTP/2.0");
        Mockito.doReturn("enroll.lanveriq.internal")
                .when(conflictingAuthority).getServerName();
        assertThrows(SecurityException.class,
                () -> policy.resolve(conflictingAuthority, deviceId, "activate"));
        assertDenied(request -> {
            request.setProtocol("HTTP/2.0");
            request.setServerName(HOST);
            request.removeHeader("Host");
            request.addHeader("Host", "console.lanveriq.internal");
        });
        assertDenied(request -> {
            request.setProtocol("HTTP/2.0");
            request.setServerName(HOST);
            request.addHeader("Host", HOST);
        });
    }

    @Test
    void missingTlsPeerCertificateIsDenied() {
        assertDenied(request -> request.removeAttribute(
                "jakarta.servlet.request.X509Certificate"));
    }

    @Test
    void insecureTlsConnectionIsDenied() {
        // Spring MockHttpServletRequest.isSecure() is secureFlag OR https
        // scheme, so clear both to model a genuine plaintext request.
        assertDenied(request -> {
            request.setSecure(false);
            request.setScheme("http");
        });
    }

    @Test
    void plaintextSchemeWithCertificateAttributeIsDenied() {
        assertDenied(request -> request.setScheme("http"));
    }

    @Test
    void wrongPinnedTlsClientCertificateIsDenied() {
        assertDenied(request -> request.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[]{syntheticTlsPeer(
                        "forged-local-peer".getBytes(StandardCharsets.UTF_8))}));
    }

    @Test
    void emptyTlsCertificateChainIsDenied() {
        assertDenied(request -> request.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[0]));
    }

    @Test
    void arbitraryServletCertificateAttributeIsDenied() {
        assertDenied(request -> request.setAttribute(
                "jakarta.servlet.request.X509Certificate", "spoofed"));
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
                () -> new AgentIngressAssertionPolicy("https://agent.lanveriq.internal",
                        sha256(CADDY_CLIENT_DER)));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentIngressAssertionPolicy(HOST, ""));
    }

    @Test
    void exactCurrentEpochGetRequiresPinnedTlsAndRejectsSpoofedIdentity() {
        var request = request();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/devices/" + deviceId + "/heartbeats/credential-epoch");
        assertEquals(FINGERPRINT, policy.resolveCurrentCredentialEpoch(
                request, deviceId).sha256Fingerprint());
        request.setQueryString("epoch=forged");
        assertThrows(SecurityException.class,
                () -> policy.resolveCurrentCredentialEpoch(request, deviceId));
        request.setQueryString(null);
        request.setMethod("POST");
        assertThrows(SecurityException.class,
                () -> policy.resolveCurrentCredentialEpoch(request, deviceId));
        request.setMethod("GET");
        request.addHeader("X-Lanveriq-Verified-Client-Epoch", "forged");
        assertThrows(SecurityException.class,
                () -> policy.resolveCurrentCredentialEpoch(request, deviceId));

        var wrong = request();
        wrong.setMethod("GET");
        wrong.setRequestURI("/api/v1/devices/" + deviceId + "/heartbeats/credential-epoch");
        wrong.setContent("unexpected".getBytes(StandardCharsets.UTF_8));
        assertThrows(SecurityException.class,
                () -> policy.resolveCurrentCredentialEpoch(wrong, deviceId));
        wrong.setContent(new byte[0]);
        wrong.setRemoteAddr("192.0.2.1");
        assertThrows(SecurityException.class,
                () -> policy.resolveCurrentCredentialEpoch(wrong, deviceId));
        wrong.setRemoteAddr("127.0.0.1");
        wrong.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[0]);
        assertThrows(SecurityException.class,
                () -> policy.resolveCurrentCredentialEpoch(wrong, deviceId));
    }

    private static X509Certificate syntheticTlsPeer(byte[] encoded) {
        try {
            var certificate = Mockito.mock(X509Certificate.class);
            Mockito.when(certificate.getEncoded()).thenReturn(encoded);
            // A mock is NOT a TLS proof. Only the actual Tomcat client-auth=need
            // TLS connector may provide this attribute at real deployment.
            return certificate;
        } catch (java.security.cert.CertificateEncodingException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private int assertionCase;

    private void assertDenied(Consumer<MockHttpServletRequest> mutation) {
        var request = request();
        mutation.accept(request);
        int scenario = ++assertionCase;
        assertThrows(SecurityException.class,
                () -> policy.resolve(request, deviceId, "activate"),
                "Expected denial for synthetic request mutation #" + scenario);
    }
}
