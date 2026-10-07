/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SealedDocument;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDefaultObjectFactory;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

/**
 * Seals PDF documents as PAdES {@code BASELINE-T} with a key it is handed, or as {@code BASELINE-B}
 * when there is no timestamp to be had.
 *
 * <p>The seal is a CMS signature in the PDF's signature dictionary, written by DSS on its PDFBox
 * backend as an incremental update, so the bytes of the original document stay untouched and
 * covered. It holds the signing certificate and the chain it is given, a signing time from this
 * server's clock, and SHA-256 digests. With timestamps on, the signature also carries a timestamp
 * from the first of the {@link TimestampServices} that answers, which proves the seal existed at
 * that time. When none answers, the document is sealed at {@code BASELINE-B} instead of failing and
 * the result says so; {@link #addTimestamp} lifts such a document to {@code BASELINE-T} later.
 *
 * <p>No revocation data or chain validation is embedded ({@code B-LT}), and no key is stored,
 * created or looked up here. The caller owns the key.
 *
 * <p>The timestamp services are the only network calls. The certificate verifier is built without an
 * AIA source, no CRL or OCSP source is set, and the EU trusted lists are never loaded: the module that
 * would fetch them is not a dependency.
 */
@Singleton
public class PdfSealer {
    private static final Logger log = LoggerFactory.getLogger(PdfSealer.class);
    private static final DigestAlgorithm DIGEST = DigestAlgorithm.SHA256;

    private final TimestampServices timestamps;

    /**
     * Creates a sealer that stamps through the given services.
     *
     * @param timestamps the timestamp services, which may be none
     */
    @Inject
    public PdfSealer(TimestampServices timestamps) {
        this.timestamps = timestamps;
    }

    /**
     * Seals a PDF document, with a timestamp when one of the services gives one.
     *
     * @param pdf   the document to seal
     * @param key   the private key of the first certificate in {@code chain}
     * @param chain the signing certificate first, followed by the certificates that issued it
     * @return the sealed document, the original bytes followed by the signature revision, with the
     *     level it reached and the service that stamped it
     * @throws IllegalArgumentException when the chain is empty
     * @throws IllegalStateException    when the key cannot produce a signature
     */
    public SealedDocument seal(byte[] pdf, PrivateKey key, List<X509Certificate> chain) {
        if (chain.isEmpty()) throw new IllegalArgumentException("A seal needs at least the signing certificate");
        var round = timestamps.round();
        if (round.isPresent()) {
            try {
                var sealed = sign(pdf, key, chain, SignatureLevel.PAdES_BASELINE_T, round.get());
                return SealedDocument.timestamped(sealed, answeredBy(round.get()));
            } catch (DSSException e) {
                log.warn("Sealed a document without a timestamp: {}", e.getMessage());
            }
        }
        return SealedDocument.withoutTimestamp(sign(pdf, key, chain, SignatureLevel.PAdES_BASELINE_B, null));
    }

    /**
     * Adds a document timestamp to a sealed document, which lifts a {@code BASELINE-B} seal to
     * {@code BASELINE-T}.
     *
     * <p>The timestamp goes into a revision of its own, so the bytes the earlier seals cover stay as
     * they are and every one of them stays valid. Meant for documents sealed while no timestamp
     * service answered; nothing calls it yet, because sealed documents are not stored yet. A document
     * that already carries a timestamp gets one more.
     *
     * @param sealed a document holding at least one seal
     * @return the document with the timestamp, or the document unchanged at {@code BASELINE-B} when
     *     timestamps are off or no service answered
     */
    public SealedDocument addTimestamp(byte[] sealed) {
        var round = timestamps.round();
        if (round.isEmpty()) return SealedDocument.withoutTimestamp(sealed);
        var parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_T);
        parameters.setDigestAlgorithm(DIGEST);
        try {
            var extended = service(round.get()).extendDocument(new InMemoryDocument(sealed), parameters);
            return SealedDocument.timestamped(DSSUtils.toByteArray(extended), answeredBy(round.get()));
        } catch (DSSException e) {
            log.warn("Could not add a timestamp to a sealed document: {}", e.getMessage());
            return SealedDocument.withoutTimestamp(sealed);
        }
    }

    private static byte[] sign(
            byte[] pdf,
            PrivateKey key,
            List<X509Certificate> chain,
            SignatureLevel level,
            @Nullable TSPSource timestampSource) {
        var service = service(timestampSource);
        var parameters = parameters(chain, level);
        var document = new InMemoryDocument(pdf);
        var dataToSign = service.getDataToSign(document, parameters);
        var value = sign(dataToSign, key);
        return DSSUtils.toByteArray(service.signDocument(document, parameters, value));
    }

    private static PAdESService service(@Nullable TSPSource timestampSource) {
        var service = new PAdESService(new CommonCertificateVerifier(true));
        service.setPdfObjFactory(new PdfBoxDefaultObjectFactory());
        if (timestampSource != null) service.setTspSource(timestampSource);
        return service;
    }

    private static String answeredBy(TimestampServices.Round round) {
        return Objects.requireNonNull(round.answeredBy(), "A timestamp was embedded, so a service answered");
    }

    private static PAdESSignatureParameters parameters(List<X509Certificate> chain, SignatureLevel level) {
        var parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(level);
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
