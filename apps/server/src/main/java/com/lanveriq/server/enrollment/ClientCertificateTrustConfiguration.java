package com.lanveriq.server.enrollment;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Disabled by default. Explicitly pins an offline-provisioned public client
 * CA file. Never use OS/web root stores, client-requested CAs, or TLS bypass.
 * No CA private key is loaded by Spring or stored in the repository.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lanveriq.client-cert-registration", name = "enabled",
        havingValue = "true")
class ClientCertificateTrustConfiguration {

    @Bean
    ClientCertificateProfileValidator clientCertificateProfileValidator(Environment environment) {
        String configured = Objects.requireNonNull(
                environment.getProperty("lanveriq.client-cert-registration.trusted-ca-file"),
                "Explicit dedicated client CA PEM path required");
        try {
            Path path = Path.of(configured);
            if (!path.isAbsolute() || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path) || Files.size(path) > 16_384
                    || Files.size(path) < 256) {
                throw new IllegalStateException("Invalid dedicated client CA file");
            }
            String pem = Files.readString(path, StandardCharsets.US_ASCII);
            if (!pem.startsWith("-----BEGIN CERTIFICATE-----")
                    || !pem.stripTrailing().endsWith("-----END CERTIFICATE-----")
                    || pem.split("-----BEGIN CERTIFICATE-----", -1).length != 2
                    || pem.contains("PRIVATE KEY")) {
                throw new IllegalStateException("Dedicated client CA must contain one public PEM certificate");
            }
            var factory = CertificateFactory.getInstance("X.509");
            var cert = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            return new ClientCertificateProfileValidator(cert, Clock.systemUTC());
        } catch (java.io.IOException | java.security.cert.CertificateException e) {
            throw new IllegalStateException("Dedicated client CA cannot be loaded", e);
        }
    }
}
