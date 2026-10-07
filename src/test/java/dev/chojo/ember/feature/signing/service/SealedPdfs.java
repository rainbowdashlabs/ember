/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SealingKey;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.jaxb.object.Message;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDefaultObjectFactory;
import eu.europa.esig.dss.spi.policy.SignaturePolicyProvider;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.spi.x509.revocation.crl.ExternalResourcesCRLSource;
import eu.europa.esig.dss.validation.reports.Reports;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * A small document to seal and an offline DSS validation of a sealed one, for the sealing tests.
 */
final class SealedPdfs {
    /** The title in the document's information dictionary, readable in the uncompressed bytes. */
    static final String TITLE = "Ember seal test";

    private SealedPdfs() {}

    /** @return an uncompressed one-page PDF titled {@value #TITLE} */
    static byte[] onePagePdf() throws IOException {
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

    /**
     * Seals {@link #onePagePdf()} with a station key, without a timestamp.
     *
     * @param key the station key
     * @return the sealed document
     */
    static byte[] sealedWith(SealingKey key) throws IOException {
        return new PdfSealer(TimestampServices.none())
                .seal(onePagePdf(), key.privateKey(), key.chain())
                .pdf();
    }

    /**
     * Validates a sealed document with DSS, offline, trusting one certificate.
     *
     * @param sealed          the sealed document
     * @param trustAnchor     the only certificate the validation trusts
     * @param revocationLists DER encoded revocation lists the validation may consult, none for a
     *                        validation without revocation data
     * @return the validation reports
     */
    static Reports validate(byte[] sealed, X509Certificate trustAnchor, byte[]... revocationLists) {
        return validate(sealed, List.of(trustAnchor), revocationLists);
    }

    /**
     * Validates a sealed document with DSS, offline, trusting the given certificates.
     *
     * @param sealed          the sealed document
     * @param trustAnchors    the only certificates the validation trusts
     * @param revocationLists DER encoded revocation lists the validation may consult
     * @return the validation reports
     */
    static Reports validate(byte[] sealed, List<X509Certificate> trustAnchors, byte[]... revocationLists) {
        var trusted = new CommonTrustedCertificateSource();
        trustAnchors.forEach(anchor -> trusted.addCertificate(new CertificateToken(anchor)));
        var verifier = new CommonCertificateVerifier(true);
        verifier.setTrustedCertSources(trusted);
        if (revocationLists.length > 0) {
            verifier.setCrlSource(new ExternalResourcesCRLSource(
                    Arrays.stream(revocationLists).map(InMemoryDocument::new).toArray(DSSDocument[]::new)));
        }

        var validator = new PDFDocumentValidator(new InMemoryDocument(sealed));
        validator.setPdfObjFactory(new PdfBoxDefaultObjectFactory());
        validator.setCertificateVerifier(verifier);
        validator.setSignaturePolicyProvider(new SignaturePolicyProvider());
        return validator.validateDocument();
    }

    /** @return every AdES validation error the simple report lists, over all signatures */
    static List<String> validationErrors(Reports reports) {
        var report = reports.getSimpleReport();
        return report.getSignatureIdList().stream()
                .flatMap(id -> report.getAdESValidationErrors(id).stream())
                .map(Message::getValue)
                .toList();
    }

    /** @return the URIs a certificate names as its CRL distribution points, in order */
    static List<String> distributionPointsOf(X509Certificate certificate) throws IOException {
        var extension = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId());
        if (extension == null) return List.of();
        var points = CRLDistPoint.getInstance(JcaX509ExtensionUtils.parseExtensionValue(extension));
        return Arrays.stream(points.getDistributionPoints())
                .flatMap(point -> Arrays.stream(
                        GeneralNames.getInstance(point.getDistributionPoint().getName())
                                .getNames()))
                .map(name -> name.getName().toString())
                .toList();
    }

    /** @return where {@code needle} first occurs in {@code haystack}, or -1 */
    static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    /** @return whether every piece of data the signature references was found and is unchanged */
    static boolean referencedDataIntact(SignatureWrapper signature) {
        var matchers = signature.getDigestMatchers();
        return !matchers.isEmpty()
                && matchers.stream().allMatch(matcher -> matcher.isDataFound() && matcher.isDataIntact());
    }
}
