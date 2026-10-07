/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDefaultObjectFactory;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import jakarta.inject.Singleton;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Seals PDF documents as PAdES {@code BASELINE-B} with a key it is handed.
 *
 * <p>The seal is a CMS signature in the PDF's signature dictionary, written by DSS on its PDFBox
 * backend as an incremental update, so the bytes of the original document stay untouched and
 * covered. It holds the signing certificate and the chain it is given, a signing time that is only
 * this server's clock, and SHA-256 digests.
 *
 * <p>It deliberately does no more than that. There is no timestamp from a timestamp authority, so
 * the level stays {@code B-B} and the signing time proves nothing on its own; no revocation data or
 * chain validation is embedded ({@code B-LT}); and no key is stored, created or looked up here. The
 * caller owns the key.
 *
 * <p>Nothing here reaches the network. The certificate verifier is built without an AIA source, no
 * timestamp, CRL or OCSP source is set, and the EU trusted lists are never loaded: the module that
 * would fetch them is not a dependency.
 */
@Singleton
public class PdfSealer {
    private static final DigestAlgorithm DIGEST = DigestAlgorithm.SHA256;

    private final PAdESService service;

    /** Creates a sealer with an offline certificate verifier and the PDFBox backend. */
    public PdfSealer() {
        service = new PAdESService(new CommonCertificateVerifier(true));
        service.setPdfObjFactory(new PdfBoxDefaultObjectFactory());
    }

    /**
     * Seals a PDF document.
     *
     * @param pdf   the document to seal
     * @param key   the private key of the first certificate in {@code chain}
     * @param chain the signing certificate first, followed by the certificates that issued it
     * @return the sealed document, the original bytes followed by the signature revision
     * @throws IllegalArgumentException when the chain is empty
     * @throws IllegalStateException    when the key cannot produce a signature
     */
    public byte[] seal(byte[] pdf, PrivateKey key, List<X509Certificate> chain) {
        if (chain.isEmpty()) throw new IllegalArgumentException("A seal needs at least the signing certificate");
        var parameters = parameters(chain);
        var document = new InMemoryDocument(pdf);
        var dataToSign = service.getDataToSign(document, parameters);
        var value = sign(dataToSign, key);
        return DSSUtils.toByteArray(service.signDocument(document, parameters, value));
    }

    private static PAdESSignatureParameters parameters(List<X509Certificate> chain) {
        var parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        parameters.setDigestAlgorithm(DIGEST);
        parameters.setSigningCertificate(new CertificateToken(chain.getFirst()));
        parameters.setCertificateChain(chain.stream().map(CertificateToken::new).toList());
        return parameters;
    }

    private static SignatureValue sign(ToBeSigned dataToSign, PrivateKey key) {
        var algorithm = SignatureAlgorithm.getAlgorithm(EncryptionAlgorithm.forKey(key), DIGEST);
        try {
            var signature = Signature.getInstance(algorithm.getJCEId());
            signature.initSign(key);
            signature.update(dataToSign.getBytes());
            return new SignatureValue(algorithm, signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The key could not sign the seal", e);
        }
    }
}
