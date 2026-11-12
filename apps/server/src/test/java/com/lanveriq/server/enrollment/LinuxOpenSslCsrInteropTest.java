package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Actual Linux Rust offline-CSR output is signed by a synthetic OpenSSL 3 CA
 * in a separate privileged CI process; only PUBLIC PEMs reach this Java test.
 * The real server-side validators must accept/reject those exact wire files.
 * This test cannot authorize CA signing, an actual device or live activation.
 */
@EnabledIfEnvironmentVariable(named = "LANVERIQ_SYNTHETIC_LINUX_PKI_DIR", matches = ".+")
class LinuxOpenSslCsrInteropTest {
    private static final UUID DEVICE_ID =
            UUID.fromString("71d3df4a-c892-47c7-9d44-83666a3cb3ab");

    @Test
    void rustGeneratedP256CsrAndSameSpkiOpenSslIssuedLeafPassRealServerValidators()
            throws Exception {
        Path dir = fixtureDir();
        String csrText = text(dir, "device-primary.csr.pem");
        assertTrue(csrText.startsWith("-----BEGIN CERTIFICATE REQUEST-----"));
        assertFalse(csrText.contains("PRIVATE KEY"));
        var csr = new CertificateSigningRequestValidator().validate(csrText);
        assertEquals(64, csr.csrSha256Digest().length());
        assertEquals(64, csr.spkiSha256Digest().length());
        assertEquals(csr.csrSha256Digest(),
                new CertificateSigningRequestValidator().validate(csrText).csrSha256Digest());
        var root = certificate(dir, "trusted-root.pem");
        var leaf = certificate(dir, "client-leaf.pem");
        var verified = new ClientCertificateProfileValidator(root, Clock.systemUTC())
                .validate(text(dir, "client-leaf.pem"), DEVICE_ID, csr.spkiSha256Digest());
        assertEquals(csr.spkiSha256Digest(), verified.csrSpkiSha256());
        assertEquals(sha256(leaf.getPublicKey().getEncoded()), verified.csrSpkiSha256());
        assertEquals(sha256(leaf.getEncoded()), verified.fingerprint());
        assertEquals(sha256(root.getEncoded()), verified.issuerId());
        assertTrue(verified.expiresAt().isAfter(verified.issuedAt()));
        assertNotEquals(verified.issuerId(), verified.fingerprint());
    }

    @Test
    void wrongDeviceUriServerOnlyEkuDifferentKeyOrTrustRootFailClosed() throws Exception {
        Path dir = fixtureDir();
        var csr = new CertificateSigningRequestValidator()
                .validate(text(dir, "device-primary.csr.pem"));
        var root = certificate(dir, "trusted-root.pem");
        var trusted = new ClientCertificateProfileValidator(root, Clock.systemUTC());
        String valid = text(dir, "client-leaf.pem");
        assertThrows(IllegalArgumentException.class, () ->
                trusted.validate(valid,
                        UUID.fromString("ce046b24-d3f1-407f-a6cd-913500d74bb9"),
                        csr.spkiSha256Digest()));
        assertThrows(IllegalArgumentException.class, () ->
                trusted.validate(text(dir, "wrong-san-leaf.pem"),
                        DEVICE_ID, csr.spkiSha256Digest()));
        assertThrows(IllegalArgumentException.class, () ->
                trusted.validate(text(dir, "server-auth-leaf.pem"),
                        DEVICE_ID, csr.spkiSha256Digest()));
        assertThrows(IllegalArgumentException.class, () ->
                trusted.validate(text(dir, "alternate-key-leaf.pem"),
                        DEVICE_ID, csr.spkiSha256Digest()));
        assertThrows(IllegalArgumentException.class, () ->
                new ClientCertificateProfileValidator(
                        certificate(dir, "wrong-root.pem"), Clock.systemUTC())
                        .validate(valid, DEVICE_ID, csr.spkiSha256Digest()));
        assertThrows(IllegalArgumentException.class, () ->
                trusted.validate(valid, DEVICE_ID, "f".repeat(64)));
    }

    @Test
    void tamperedLinuxCsrSignatureAndPrivateMaterialAreNotAccepted() throws Exception {
        Path dir = fixtureDir();
        String csr = text(dir, "device-primary.csr.pem");
        String content = csr.replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                .replace("-----END CERTIFICATE REQUEST-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(content);
        der[der.length - 1] ^= 1;
        String corrupt = "-----BEGIN CERTIFICATE REQUEST-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
                + "\n-----END CERTIFICATE REQUEST-----\n";
        assertThrows(IllegalArgumentException.class,
                () -> new CertificateSigningRequestValidator().validate(corrupt));
        for (String name : new String[] {
                "device-primary.csr.pem", "trusted-root.pem", "wrong-root.pem",
                "client-leaf.pem", "wrong-san-leaf.pem", "server-auth-leaf.pem",
                "alternate-key-leaf.pem"
        }) {
            String publicPem = text(dir, name);
            assertFalse(publicPem.contains("PRIVATE KEY"));
            assertTrue(publicPem.length() < 32_768);
        }
    }

    private static Path fixtureDir() {
        Path dir = Path.of(System.getenv("LANVERIQ_SYNTHETIC_LINUX_PKI_DIR"));
        assertTrue(dir.isAbsolute() && Files.isDirectory(dir));
        assertEquals("lanveriq-linux-pki-public", dir.getFileName().toString());
        return dir;
    }

    private static String text(Path dir, String name) throws Exception {
        return Files.readString(dir.resolve(name), StandardCharsets.US_ASCII);
    }

    private static X509Certificate certificate(Path dir, String name) throws Exception {
        try (InputStream input = Files.newInputStream(dir.resolve(name))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
