/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SealedDocument;
import eu.europa.esig.dss.alert.LogOnStatusAlert;
import eu.europa.esig.dss.alert.SilentOnStatusAlert;
import eu.europa.esig.dss.alert.exception.AlertException;
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
import eu.europa.esig.dss.pades.PAdESTimestampParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDefaultObjectFactory;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

/**
 * Seals PDF documents as PAdES {@code BASELINE-LT} with a key it is handed, or as far towards it as
 * the timestamp services allow.
 *
 * <p>The seal is a CMS signature in the PDF's signature dictionary, written by DSS on its PDFBox
 * backend as an incremental update, so the bytes of the original document stay untouched and
 * covered. It holds the signing certificate and the chain it is given, a signing time from this
 * server's clock, and SHA-256 digests. With timestamps on, the signature also carries a timestamp
 * from the first of the {@link TimestampServices} that answers with a timestamp chaining to the root
 * pinned for it, which proves the seal existed at that time ({@code BASELINE-T}). A further revision
 * then adds the validation material, so the seal
 * can be checked later without asking anyone ({@code BASELINE-LT}): every certificate of the seal and
 * of the timestamp, the current revocation list of the installation authority that issued the
 * station certificate, and the revocation data of the timestamp service's certificates.
 *
 * <p>Each step that cannot be completed leaves the seal one level lower instead of failing, and the
 * result says which level it reached: without a timestamp it is {@code BASELINE-B}, and with a
 * timestamp whose revocation data could not be fetched it is {@code BASELINE-T}. {@link #lift} takes
 * such a document further later.
 *
 * <p>A station key its authority's list names as revoked cannot seal: DSS checks the signing
 * certificate against that list before signing and refuses.
 *
 * <p>The outside calls are those of {@link TimestampServices}: the timestamp, and the revocation data
 * of the timestamp service's certificates, both inside one budget per seal (see
 * {@link SealRevocationSources}). The installation's own certificates are checked against the lists
 * in its database, never over the network. The certificate verifier is built without an AIA source,
 * so no issuer certificate is ever downloaded, and the EU trusted lists are never loaded: the module
 * that would fetch them is not a dependency.
 */
@Singleton
public class PdfSealer {
    private static final Logger log = LoggerFactory.getLogger(PdfSealer.class);
    private static final DigestAlgorithm DIGEST = DigestAlgorithm.SHA256;

    private final TimestampServices timestamps;
    private final StationKeyRevocations revocations;

    /**
     * Creates a sealer that stamps through the given services.
     *
     * @param timestamps  the timestamp services, which may be none
     * @param revocations the revocation lists of the installation's authorities
     */
    @Inject
    public PdfSealer(TimestampServices timestamps, StationKeyRevocations revocations) {
        this.timestamps = timestamps;
        this.revocations = revocations;
    }

    /**
     * Seals a PDF document, with a timestamp and its validation material when the services give them.
     *
     * @param pdf   the document to seal
     * @param key   the private key of the first certificate in {@code chain}
     * @param chain the signing certificate first, followed by the certificates that issued it
     * @return the sealed document, the original bytes followed by the signature revision and, at
     *     {@code BASELINE-LT}, the revision with the validation material, with the level it reached and
     *     the service that stamped it
     * @throws IllegalArgumentException when the chain is empty
     * @throws IllegalStateException    when the key cannot produce a signature
     * @throws AlertException           when the revocation list of the key's authority names it
     */
    public SealedDocument seal(byte[] pdf, PrivateKey key, List<X509Certificate> chain) {
        if (chain.isEmpty()) throw new IllegalArgumentException("A seal needs at least the signing certificate");
        var round = timestamps.round();
        if (round.isEmpty()) {
            return SealedDocument.withoutTimestamp(sign(pdf, key, chain, SignatureLevel.PAdES_BASELINE_B, null));
        }
        byte[] stamped;
        try {
            stamped = sign(pdf, key, chain, SignatureLevel.PAdES_BASELINE_T, round.get());
        } catch (DSSException e) {
            log.warn("Sealed a document without a timestamp: {}", e.getMessage());
            return SealedDocument.withoutTimestamp(sign(pdf, key, chain, SignatureLevel.PAdES_BASELINE_B, null));
        }
        return withValidationMaterial(stamped, round.get());
    }

    /**
     * Seals a PDF document at {@code BASELINE-B} without asking any timestamp service, for a document
     * that says on its face that no service answered when it was sealed a moment before.
     *
     * @param pdf   the document to seal
     * @param key   the private key of the first certificate in {@code chain}
     * @param chain the signing certificate first, followed by the certificates that issued it
     * @return the sealed document at {@code BASELINE-B}
     * @throws IllegalArgumentException when the chain is empty
     * @throws IllegalStateException    when the key cannot produce a signature
     * @throws AlertException           when the revocation list of the key's authority names it
     */
    public SealedDocument sealWithoutTimestamp(byte[] pdf, PrivateKey key, List<X509Certificate> chain) {
        if (chain.isEmpty()) throw new IllegalArgumentException("A seal needs at least the signing certificate");
        return SealedDocument.withoutTimestamp(sign(pdf, key, chain, SignatureLevel.PAdES_BASELINE_B, null));
    }

    /**
     * Takes a sealed document as far towards {@code BASELINE-LT} as the services allow now: adds a
     * document timestamp, which lifts a {@code BASELINE-B} seal to {@code BASELINE-T}, and then the
     * validation material of the seal and of every timestamp.
     *
     * <p>Each addition goes into a revision of its own, so the bytes the earlier seals cover stay as
     * they are and every one of them stays valid. Meant for documents sealed while no timestamp
     * service answered or their revocation data could not be fetched; nothing calls it yet. A document
     * that already carries a timestamp gets one more.
     *
     * @param sealed a document holding at least one seal
     * @return the document with what could be added, or the document unchanged at {@code BASELINE-B}
     *     when timestamps are off or no service answered
     */
    public SealedDocument lift(byte[] sealed) {
        var round = timestamps.round();
        if (round.isEmpty()) return SealedDocument.withoutTimestamp(sealed);
        byte[] stamped;
        try {
            stamped = documentTimestamped(sealed, round.get());
        } catch (DSSException e) {
            log.warn("Could not add a timestamp to a sealed document: {}", e.getMessage());
            return SealedDocument.withoutTimestamp(sealed);
        }
        return withValidationMaterial(stamped, round.get());
    }

    private SealedDocument withValidationMaterial(byte[] stamped, TimestampServices.Round round) {
        var stampedBy = answeredBy(round);
        try {
            return SealedDocument.longTerm(extend(stamped, SignatureLevel.PAdES_BASELINE_LT, round), stampedBy);
        } catch (DSSException | AlertException e) {
            log.warn("Sealed a document with a timestamp but without its validation material: {}", e.getMessage());
            return SealedDocument.timestamped(stamped, stampedBy);
        }
    }

    private byte[] sign(
            byte[] pdf,
            PrivateKey key,
            List<X509Certificate> chain,
            SignatureLevel level,
            TimestampServices.@Nullable Round round) {
        var service = service(round, false);
        var parameters = parameters(chain, level);
        var document = new InMemoryDocument(pdf);
        var dataToSign = service.getDataToSign(document, parameters);
        var value = sign(dataToSign, key);
        return DSSUtils.toByteArray(service.signDocument(document, parameters, value));
    }

    private byte[] documentTimestamped(byte[] sealed, TimestampServices.Round round) {
        var parameters = new PAdESTimestampParameters();
        parameters.setDigestAlgorithm(DIGEST);
        return DSSUtils.toByteArray(service(round, false).timestamp(new InMemoryDocument(sealed), parameters));
    }

    private byte[] extend(byte[] sealed, SignatureLevel level, TimestampServices.Round round) {
        var parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(level);
        parameters.setDigestAlgorithm(DIGEST);
        var service = service(round, level == SignatureLevel.PAdES_BASELINE_LT);
        return DSSUtils.toByteArray(service.extendDocument(new InMemoryDocument(sealed), parameters));
    }

    /**
     * @param round               the seal's outside calls, or null when nothing may be fetched
     * @param addsValidationData  whether this is the step adding the validation material: there a
     *                            certificate without revocation data fails the step, while the other
     *                            steps only log it, and a document whose seal another document
     *                            timestamp already took past {@code BASELINE-LT} still gets the material
     *                            for its newest timestamp
     */
    private PAdESService service(TimestampServices.@Nullable Round round, boolean addsValidationData) {
        var verifier = new CommonCertificateVerifier(true);
        SealRevocationSources.configure(verifier, revocations, round);
        if (addsValidationData) {
            verifier.setAugmentationAlertOnHigherSignatureLevel(new SilentOnStatusAlert());
        } else {
            verifier.setAlertOnMissingRevocationData(new LogOnStatusAlert(Level.WARN));
        }
        var service = new PAdESService(verifier);
        service.setPdfObjFactory(new PdfBoxDefaultObjectFactory());
        if (round != null) service.setTspSource(round);
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
        parameters.setCheckCertificateRevocation(true);
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
