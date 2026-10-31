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
