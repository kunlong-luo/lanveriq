package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PendingEnrollmentServiceTest {

    private final PendingEnrollmentRepository repository = Mockito.mock(PendingEnrollmentRepository.class);
    private final PendingEnrollmentService service = new PendingEnrollmentService(repository);
    private final EnrollmentTokenCodec tokens = new EnrollmentTokenCodec();

    @Test
    void passesVerifierAndValidatedCsrToSingleAtomicRepositoryOperation() throws Exception {
        var token = tokens.generate();
        String csr = validCsr();
        var expected = new PendingEnrollmentRepository.PendingDevice(UUID.randomUUID(), UUID.randomUUID());
        when(repository.consumeAndRegister(any(byte[].class), eq(9L),
                any(CertificateSigningRequestValidator.ValidatedCsr.class))).thenReturn(expected);

        var result = service.register(token.plaintext(), 9L, csr);

        assertEquals(expected, result);
        ArgumentCaptor<byte[]> digest = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<CertificateSigningRequestValidator.ValidatedCsr> request =
                ArgumentCaptor.forClass(CertificateSigningRequestValidator.ValidatedCsr.class);
        verify(repository).consumeAndRegister(digest.capture(), eq(9L), request.capture());
        assertArrayEquals(token.verifier(), digest.getValue());
        assertEquals(csr, request.getValue().publicPem());
        assertEquals(64, request.getValue().spkiSha256Digest().length());
    }

    @Test
    void invalidNoticeOrInvalidRequestCannotConsumeToken() {
        String token = tokens.generate().plaintext();
        assertThrows(IllegalArgumentException.class, () -> service.register(token, 0, "bad"));
        assertThrows(IllegalArgumentException.class, () -> service.register("bad", 1, "bad"));
        assertThrows(IllegalArgumentException.class, () -> service.register(token, 1, "bad"));
        verify(repository, never()).consumeAndRegister(any(), any(Long.class), any());
    }

    private static String validCsr() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = gen.generateKeyPair();
        var csr = new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=untrusted"), pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate()));
        StringWriter out = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(out)) {
            writer.writeObject(csr);
        }
        return out.toString();
    }
}
