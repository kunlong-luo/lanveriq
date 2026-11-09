package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Independent Java/Bouncy Castle verifier for a public PKCS#10 CSR actually
 * signed using a non-exportable Windows CNG ECDSA P-256 key on an ephemeral
 * Windows runner. The public CSR is the only cross-job artifact; its private
 * CNG key must already have been deleted before this Linux test starts.
 *
 * This does not authorize signed leaf issuance or start an Agent service.
 */
class WindowsCngCsrInteropTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "LANVERIQ_SYNTHETIC_CNG_CSR", matches = ".+")
    void independentlyVerifiesWindowsCngSignatureAndRejectsTamperedCsr() throws Exception {
        String location = System.getenv("LANVERIQ_SYNTHETIC_CNG_CSR");
        Path fixture = Path.of(location);
        assertTrue(fixture.isAbsolute(), "Synthetic Windows CSR fixture path must be absolute");
        String pem = Files.readString(fixture, StandardCharsets.US_ASCII);
        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE REQUEST-----"));
        assertTrue(pem.stripTrailing().endsWith("-----END CERTIFICATE REQUEST-----"));
        assertFalse(pem.contains("PRIVATE KEY"));
        assertTrue(pem.length() < CertificateSigningRequestValidator.MAX_PEM_BYTES);

        var validator = new CertificateSigningRequestValidator();
        var approved = validator.validate(pem);
        assertEquals(pem, approved.publicPem());
        assertEquals(64, approved.csrSha256Digest().length());
        assertEquals(64, approved.spkiSha256Digest().length());
        assertEquals(approved.csrSha256Digest(), validator.validate(pem).csrSha256Digest());
        assertNotEquals(approved.csrSha256Digest(), approved.spkiSha256Digest());

        String encoded = pem.replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                .replace("-----END CERTIFICATE REQUEST-----", "")
                .replaceAll("\\s", "");
        byte[] modified = Base64.getDecoder().decode(encoded);
        modified[modified.length - 1] ^= 0x01;
        String broken = "-----BEGIN CERTIFICATE REQUEST-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(modified)
                + "\n-----END CERTIFICATE REQUEST-----\n";
        assertThrows(IllegalArgumentException.class, () -> validator.validate(broken),
                "Bouncy Castle must reject a modified Windows CNG signature");
    }
}
