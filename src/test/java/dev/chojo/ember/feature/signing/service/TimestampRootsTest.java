/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Signing;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.CollectionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The roots shipped for the default timestamp services, and the operator's own: every default service has
 * one, each is a self-signed certificate authority, and a real timestamp each service gave on
 * 2026-10-08 (kept under {@code signing/tsa/} in the test resources) chains to the root shipped for it
 * and to no other. Everything is checked offline.
 */
class TimestampRootsTest {
    private static final int KEY_CERT_SIGN = 5;

    @Test
    void everyDefaultServiceHasAShippedRoot() {
        assertEquals(
                new HashSet<>(new Signing().timestampUrls()),
                TimestampRoots.shippedFiles().keySet());
        for (var url : new Signing().timestampUrls()) {
            assertTrue(TimestampRoots.rootFor(url, Map.of()).isPresent(), url);
        }
    }

    @Test
    void everyShippedRootIsASelfSignedAuthority() throws Exception {
        for (var file : TimestampRoots.shippedFiles().values()) {
            var root = TimestampRoots.shipped(file);

            assertEquals(root.getSubjectX500Principal(), root.getIssuerX500Principal(), file);
            assertDoesNotThrow(() -> root.verify(root.getPublicKey()), file);
            assertTrue(root.getBasicConstraints() >= 0, file + " is a certificate authority");
            assertTrue(root.getKeyUsage()[KEY_CERT_SIGN], file + " signs certificates");
        }
    }

    @Test
    void aRealTimestampOfEachServiceChainsToItsShippedRoot() throws Exception {
        for (var file : TimestampRoots.shippedFiles().values()) {
            var token = token(file);

            assertDoesNotThrow(() -> TimestampTrust.trustedUntil(token, TimestampRoots.shipped(file)), file);
        }
    }

    @Test
    void aCertificateTheTokenCarriesBesideItsChainDoesNotShortenItsEnd() throws Exception {
        var digicert = token("digicert.pem");
        var root = root("digicert.pem");
        var expired = expiredCertificate();
        var signed = new CMSSignedData(digicert);
        var certificates = new ArrayList<>(signed.getCertificates().getMatches(null));
        certificates.add(new JcaX509CertificateHolder(expired));
        var padded = CMSSignedData.replaceCertificatesAndCRLs(
                        signed,
                        new CollectionStore<>(certificates),
                        signed.getAttributeCertificates(),
                        signed.getCRLs())
                .getEncoded();

        var end = TimestampTrust.trustedUntil(padded, root);

        assertEquals(TimestampTrust.trustedUntil(digicert, root), end);
        assertTrue(end.isAfter(expired.getNotAfter().toInstant()));
    }

    @Test
    void aRealTimestampDoesNotChainToAnotherServicesRoot() throws Exception {
        var digicert = token("digicert.pem");

        for (var file : TimestampRoots.shippedFiles().values()) {
            if (file.equals("digicert.pem")) continue;
            var other = TimestampRoots.shipped(file);
            assertThrows(UntrustedTimestampException.class, () -> TimestampTrust.trustedUntil(digicert, other), file);
        }
        assertThrows(
                UntrustedTimestampException.class,
                () -> TimestampTrust.trustedUntil(digicert, LocalTimestampService.root()));
    }

    @Test
    void somethingThatIsNoTimestampIsRefused() {
        assertThrows(
                UntrustedTimestampException.class,
                () -> TimestampTrust.trustedUntil(new byte[] {0x30, 0x03, 0x02, 0x01, 0x01}, root("apple.pem")));
    }

    @Test
    void theOperatorsRootReplacesTheShippedOneAndPinsAnAddedService(@TempDir Path directory) throws Exception {
        var file = directory.resolve("root.der");
        Files.write(file, LocalTimestampService.root().getEncoded());
        var digicert = "http://timestamp.digicert.com";
        var added = "https://tsa.example.org/tsr";
        var operator = Map.of(digicert, file.toString(), added, " " + file + " ");

        assertEquals(Optional.of(LocalTimestampService.root()), TimestampRoots.rootFor(digicert, operator));
        assertEquals(Optional.of(LocalTimestampService.root()), TimestampRoots.rootFor(added, operator));
        assertEquals(Optional.of(root("digicert.pem")), TimestampRoots.rootFor(digicert, Map.of()));
        assertTrue(TimestampRoots.rootFor(added, Map.of()).isEmpty(), "an added service has no root of its own");
    }

    @Test
    void anUnreadableOperatorRootPinsNothing(@TempDir Path directory) throws Exception {
        var garbage = directory.resolve("garbage.pem");
        Files.writeString(garbage, "not a certificate");
        var url = "https://tsa.example.org/tsr";

        assertTrue(TimestampRoots.rootFor(url, Map.of(url, garbage.toString())).isEmpty());
        assertTrue(TimestampRoots.rootFor(
                        url, Map.of(url, directory.resolve("missing.pem").toString()))
                .isEmpty());
        assertEquals(
                Optional.of(root("certum.pem")),
                TimestampRoots.rootFor("http://time.certum.pl", Map.of("http://time.certum.pl", " ")),
                "a blank entry keeps the shipped root");
    }

    @Test
    void everyRootIsTheShippedOnesAndTheReadableOperatorRoots(@TempDir Path directory) throws Exception {
        var file = directory.resolve("operator.der");
        Files.write(file, LocalTimestampService.root().getEncoded());
        var operator = Map.of(
                "https://tsa.example.org/tsr", file.toString(),
                "https://other.example.org/tsr",
                        directory.resolve("missing.pem").toString(),
                "http://time.certum.pl", " ");

        var roots = TimestampRoots.all(operator);

        var shipped = TimestampRoots.shippedFiles().values().stream()
                .map(TimestampRoots::shipped)
                .collect(Collectors.toSet());
        assertEquals(shipped.size() + 1, roots.size());
        assertTrue(roots.containsAll(shipped));
        assertTrue(roots.contains(LocalTimestampService.root()));
        assertEquals(shipped.size(), TimestampRoots.all(Map.of()).size());
    }

    @Test
    void aMissingShippedRootIsAnError() {
        assertThrows(IllegalStateException.class, () -> TimestampRoots.shipped("missing.pem"));
    }

    private static X509Certificate root(String file) {
        return TimestampRoots.shipped(file);
    }

    /** A self-signed certificate that ended long ago, earlier than any certificate of a real chain. */
    private static X509Certificate expiredCertificate() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        var keys = generator.generateKeyPair();
        var name = new X500Name("CN=Unrelated");
        var builder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.ONE,
                Date.from(Instant.parse("2000-01-01T00:00:00Z")),
                Date.from(Instant.parse("2001-01-01T00:00:00Z")),
                name,
                keys.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static byte[] token(String rootFile) throws IOException {
        var name = TimestampRoots.RESOURCES + rootFile.replace(".pem", ".tst");
        try (var in = TimestampRootsTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IOException("No test token " + name);
            return in.readAllBytes();
        }
    }
}
