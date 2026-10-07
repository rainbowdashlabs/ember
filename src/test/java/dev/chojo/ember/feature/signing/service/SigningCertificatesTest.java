/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bounds of issued certificates: serial numbers, name lengths, and a station certificate never
 * outlasting its authority.
 */
class SigningCertificatesTest {
    private final SigningCertificates certificates = new SigningCertificates();

    @Test
    void aStationCertificateNeverOutlastsItsAuthority() throws Exception {
        var authority = shortLivedAuthority(Duration.ofDays(30));

        var station = certificates.station("ember.example.org", "Station", UUID.randomUUID(), authority);

        assertEquals(
                authority.certificate().getNotAfter(), station.certificate().getNotAfter());
        station.certificate().verify(authority.certificate().getPublicKey());
    }

    @Test
    void longNamesAreCutToTheLengthReadersAccept() throws Exception {
        var authority = shortLivedAuthority(Duration.ofDays(365));
        var name = "Freiwillige Feuerwehr ".repeat(5) + "🚒";

        var station = certificates.station("ember.example.org", name, UUID.randomUUID(), authority);

        var commonName = commonNameOf(station.certificate());
        assertEquals(64, commonName.codePointCount(0, commonName.length()));
        assertTrue(name.startsWith(commonName));
    }

    @Test
    void serialNumbersAreRandomPositiveAndTwentyBytesLong() throws Exception {
        var authority = shortLivedAuthority(Duration.ofDays(365));

        var one = certificates.station("ember.example.org", "One", UUID.randomUUID(), authority);
        var other = certificates.station("ember.example.org", "Other", UUID.randomUUID(), authority);

        for (var certificate : List.of(one.certificate(), other.certificate())) {
            assertEquals(1, certificate.getSerialNumber().signum());
            assertEquals(159, certificate.getSerialNumber().bitLength());
            assertEquals(20, certificate.getSerialNumber().toByteArray().length);
        }
        assertNotEquals(one.certificate().getSerialNumber(), other.certificate().getSerialNumber());
        assertEquals(one.certificate().getSerialNumber().toString(16), SigningCertificates.serialOf(one.certificate()));
    }

    private static String commonNameOf(X509Certificate certificate) {
        var name = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        return IETFUtils.valueToString(name.getRDNs(BCStyle.CN)[0].getFirst().getValue());
    }

    private static SigningCertificates.Issued shortLivedAuthority(Duration validity) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var subject = new X500Name("CN=Short-lived authority");
        var now = Instant.now();
        var holder = new JcaX509v3CertificateBuilder(
                        subject,
                        BigInteger.ONE,
                        Date.from(now.minus(Duration.ofDays(1))),
                        Date.from(now.plus(validity)),
                        subject,
                        keys.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate()));
        return new SigningCertificates.Issued(
                keys.getPrivate(), new JcaX509CertificateConverter().getCertificate(holder));
    }
}
