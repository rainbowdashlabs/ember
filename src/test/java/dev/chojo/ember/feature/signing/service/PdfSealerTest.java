/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.jaxb.object.Message;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDefaultObjectFactory;
import eu.europa.esig.dss.spi.policy.SignaturePolicyProvider;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.reports.Reports;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSealerTest {
    private static final String TITLE = "Ember seal test";

    private static KeyPair keys;
    private static X509Certificate certificate;
    private static byte[] pdf;

    private final PdfSealer sealer = new PdfSealer();

    @BeforeAll
    static void createKeyAndDocument() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        certificate = selfSigned(keys);
        pdf = onePagePdf();
    }

    @Test
    void sealedDocumentCarriesAnIntactBaselineBSignature() {
        var sealed = sealer.seal(pdf, keys.getPrivate(), List.of(certificate));

        var reports = validate(sealed);

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
        var sealed = sealer.seal(pdf, keys.getPrivate(), List.of(certificate));

        assertTrue(sealed.length > pdf.length);
        assertArrayEquals(pdf, Arrays.copyOf(sealed, pdf.length));
    }

    @Test
    void tamperedByteInTheSignedRevisionBreaksTheSeal() {
        var sealed = sealer.seal(pdf, keys.getPrivate(), List.of(certificate));
        var position = indexOf(sealed, TITLE.getBytes(StandardCharsets.US_ASCII));
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
        var trusted = new CommonTrustedCertificateSource();
        trusted.addCertificate(new CertificateToken(certificate));
        var verifier = new CommonCertificateVerifier(true);
        verifier.setTrustedCertSources(trusted);

        var validator = new PDFDocumentValidator(new InMemoryDocument(sealed));
        validator.setPdfObjFactory(new PdfBoxDefaultObjectFactory());
        validator.setCertificateVerifier(verifier);
        validator.setSignaturePolicyProvider(new SignaturePolicyProvider());
        return validator.validateDocument();
    }

    private static List<String> validationErrors(Reports reports) {
        var report = reports.getSimpleReport();
        return report.getSignatureIdList().stream()
                .flatMap(id -> report.getAdESValidationErrors(id).stream())
                .map(Message::getValue)
                .toList();
    }

    private static boolean referencedDataIntact(SignatureWrapper signature) {
        var matchers = signature.getDigestMatchers();
        return !matchers.isEmpty()
                && matchers.stream().allMatch(matcher -> matcher.isDataFound() && matcher.isDataIntact());
    }

    private static X509Certificate selfSigned(KeyPair keys) throws Exception {
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

    private static byte[] onePagePdf() throws Exception {
        try (var document = new PDDocument();
                var out = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            document.getDocumentInformation().setTitle(TITLE);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Sealed by Ember");
                content.endText();
            }
            document.save(out, CompressParameters.NO_COMPRESSION);
            return out.toByteArray();
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        throw new AssertionError("Not found in the document");
    }
}
