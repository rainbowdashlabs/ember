/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SealLevel;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.validation.reports.Reports;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static dev.chojo.ember.feature.signing.service.SealedPdfs.TITLE;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.indexOf;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.referencedDataIntact;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.validationErrors;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSealerTest {
    private static KeyPair keys;
    private static X509Certificate certificate;
    private static byte[] pdf;

    private final PdfSealer sealer = SealedPdfs.sealer(TimestampServices.none());

    @BeforeAll
    static void createKeyAndDocument() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        certificate = selfSigned(keys);
        pdf = SealedPdfs.onePagePdf();
    }

    @Test
    void sealedDocumentCarriesAnIntactBaselineBSignature() {
        var result = sealer.seal(pdf, keys.getPrivate(), List.of(certificate));
        var sealed = result.pdf();

        var reports = validate(sealed);

        assertEquals(SealLevel.BASELINE_B, result.level());
        assertNull(result.timestampedBy());

        var signatures = reports.getDiagnosticData().getSignatures();
        assertEquals(1, signatures.size());
        var signature = signatures.getFirst();
        assertEquals(SignatureLevel.PAdES_BASELINE_B, signature.getSignatureFormat());
        assertTrue(signature.isSignatureIntact(), "signature value");
        assertTrue(signature.isSignatureValid(), "signature value and signed data");
        assertTrue(referencedDataIntact(signature), "signed data");
        assertTrue(signature.isSigningCertificateIdentified(), "signing certificate");
        assertEquals(List.of(), validationErrors(reports));
    }

    @Test
    void sealKeepsTheOriginalBytesAsTheFirstRevision() {
        var sealed = sealer.seal(pdf, keys.getPrivate(), List.of(certificate)).pdf();

        assertTrue(sealed.length > pdf.length);
        assertArrayEquals(pdf, Arrays.copyOf(sealed, pdf.length));
    }

    @Test
    void tamperedByteInTheSignedRevisionBreaksTheSeal() {
        var sealed = sealer.seal(pdf, keys.getPrivate(), List.of(certificate)).pdf();
        var position = indexOf(sealed, TITLE.getBytes(StandardCharsets.US_ASCII));
        assertTrue(position >= 0, "title in the document");
        sealed[position] = (byte) 'e';

        var reports = validate(sealed);

        var signatures = reports.getDiagnosticData().getSignatures();
        assertEquals(1, signatures.size());
        var signature = signatures.getFirst();
        assertFalse(referencedDataIntact(signature), "signed data");
        assertFalse(signature.isSignatureValid(), "signature value and signed data");
        assertFalse(validationErrors(reports).isEmpty());
    }

    @Test
    void sealWithoutCertificateIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> sealer.seal(pdf, keys.getPrivate(), List.of()));
    }

    private static Reports validate(byte[] sealed) {
        return SealedPdfs.validate(sealed, certificate);
    }

    /** @return a self-signed seal certificate for the key pair, valid from yesterday for 30 days */
    static X509Certificate selfSigned(KeyPair keys) throws Exception {
        var subject = new X500Name("CN=Ember test seal");
        var now = Instant.now();
        var holder = new JcaX509v3CertificateBuilder(
                        subject,
                        BigInteger.valueOf(now.toEpochMilli()),
                        Date.from(now.minus(Duration.ofDays(1))),
                        Date.from(now.plus(Duration.ofDays(30))),
                        subject,
                        keys.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate()));
        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}
