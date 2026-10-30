package com.lanveriq.server.enrollment;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRV-02 internal operation: no HTTP controller until the enrollment-only
 * TLS host, notice verification and rate/size enforcement are implemented.
 */
@Service
public class PendingEnrollmentService {

    private final PendingEnrollmentRepository repository;
    private final EnrollmentTokenCodec tokens;
    private final CertificateSigningRequestValidator csrs;

    PendingEnrollmentService(PendingEnrollmentRepository repository) {
        this(repository, new EnrollmentTokenCodec(), new CertificateSigningRequestValidator());
    }

    PendingEnrollmentService(PendingEnrollmentRepository repository,
                             EnrollmentTokenCodec tokens,
                             CertificateSigningRequestValidator csrs) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.csrs = Objects.requireNonNull(csrs, "csrs");
    }

    /**
     * The notice version must come from an approved, disclosed enrollment
     * screen. A matching integer alone is NOT evidence of organizational consent.
     */
    @Transactional
    public PendingEnrollmentRepository.PendingDevice register(
            String token, long acknowledgedNoticeVersion, String csrPem) {
        if (acknowledgedNoticeVersion < 1) {
            throw new IllegalArgumentException("Missing approved notice version");
        }
        byte[] verifier = tokens.verifierFor(token);
        var csr = csrs.validate(csrPem);
        return repository.consumeAndRegister(verifier, acknowledgedNoticeVersion, csr);
    }
}
