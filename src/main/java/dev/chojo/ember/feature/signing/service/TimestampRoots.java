/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The root certificate each timestamp service's timestamps must chain to.
 *
 * <p>Ember ships the root of every default service under {@value #RESOURCES}, each file named after its
 * service and opening with the service's address, the root's subject and its SHA-256 fingerprint. They
 * were taken from the services' own answers to a real request: the token's chain was followed up to its
 * self-signed root, and where the token ends in a cross-certificate, the self-signed certificate of the
 * same root key was taken from the issuer address the chain names, so the pin outlives the
 * cross-signature.
 *
 * <p>The operator pins a root for an added service in {@code signing.timestampRoots}, by the service's
 * address spelled as in {@code signing.timestampUrls}, with the path of a PEM or DER file. An entry there
 * replaces a shipped root, which is how a default service that moved to another root keeps working
 * before Ember ships the new one. A service with no root is never asked.
 */
final class TimestampRoots {
    /** Where the shipped roots are, on the class path. */
    static final String RESOURCES = "signing/tsa/";

    private static final Logger log = LoggerFactory.getLogger(TimestampRoots.class);

    private static final Map<String, String> SHIPPED = Map.of(
            "http://timestamp.digicert.com", "digicert.pem",
            "http://timestamp.sectigo.com", "sectigo.pem",
            "http://timestamp.globalsign.com/tsa/r6advanced1", "globalsign.pem",
            "http://time.certum.pl", "certum.pem",
            "http://timestamp.acs.microsoft.com", "microsoft.pem",
            "http://timestamp.apple.com/ts01", "apple.pem",
            "https://freetsa.org/tsr", "freetsa.pem");

    private TimestampRoots() {}

    /**
     * @param url           a timestamp service's address
     * @param operatorRoots the operator's roots by service address, each the path of a certificate file
     * @return the root its timestamps must chain to: the operator's when one is configured and readable,
     *     else the shipped one, else empty
     */
    static Optional<X509Certificate> rootFor(String url, Map<String, String> operatorRoots) {
        var configured = operatorRoots.get(url);
        if (configured != null && !configured.isBlank()) return fromFile(url, configured.strip());
        return Optional.ofNullable(SHIPPED.get(url)).map(TimestampRoots::shipped);
    }

    /**
     * Every root a timestamp of this installation may chain to, whether its service is asked today or
     * not, since a document stamped while it was asked keeps its timestamp.
     *
     * @param operatorRoots the operator's roots by service address, each the path of a certificate file
     * @return each shipped root, then each operator root that can be read, without repeats
     */
    static List<X509Certificate> all(Map<String, String> operatorRoots) {
        var roots = new LinkedHashSet<X509Certificate>();
        SHIPPED.values().stream().sorted().map(TimestampRoots::shipped).forEach(roots::add);
        operatorRoots.forEach((url, file) -> {
            if (!file.isBlank()) fromFile(url, file.strip()).ifPresent(roots::add);
        });
        return List.copyOf(roots);
    }

    /** @return the addresses of the services Ember ships a root for */
    static Map<String, String> shippedFiles() {
        return SHIPPED;
    }

    /**
     * @param file a file name under {@value #RESOURCES}
     * @return the root certificate in it
     */
    static X509Certificate shipped(String file) {
        try (var in = TimestampRoots.class.getClassLoader().getResourceAsStream(RESOURCES + file)) {
            if (in == null) throw new IllegalStateException("The shipped timestamp root " + file + " is missing");
            return read(in);
        } catch (IOException | CertificateException e) {
            throw new IllegalStateException("The shipped timestamp root " + file + " cannot be read", e);
        }
    }

    private static Optional<X509Certificate> fromFile(String url, String file) {
        try (var in = Files.newInputStream(Path.of(file))) {
            return Optional.of(read(in));
        } catch (IOException | InvalidPathException | CertificateException e) {
            log.warn("The root certificate {} for timestamp service {} cannot be read: {}", file, url, e.getMessage());
            return Optional.empty();
        }
    }

    private static X509Certificate read(InputStream in) throws CertificateException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
    }
}
