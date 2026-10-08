/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Sealing for tests outside this package: a timestamp service on loopback, a sealer that stamps through
 * it, a sealer of signing states, and the small document the sealing tests seal. Nothing here reaches the
 * internet.
 */
public final class TestSealing implements AutoCloseable {
    private static final String STATE_BASE_URL = "https://ember.test";

    private final LocalTimestampService service;

    private TestSealing(LocalTimestampService service) {
        this.service = service;
    }

    /** @return a running timestamp service, to be closed after the test */
    public static TestSealing withTimestamps() throws IOException {
        return new TestSealing(LocalTimestampService.start());
    }

    /**
     * @param revocations the revocation lists of the installation's authorities
     * @return a sealer that stamps through this service and reaches {@code BASELINE-LT}
     */
    public PdfSealer sealer(StationKeyRevocations revocations) {
        return new PdfSealer(
                new TimestampServices(List.of(service.pinned()), TimestampServices.TIMEOUT, TimestampServices.BUDGET),
                revocations);
    }

    /** @return a sealer that asks no timestamp service and knows no revocation list, for a foreign seal */
    public static PdfSealer withoutTimestamps() {
        return SealedPdfs.sealer(SealedPdfs.noTimestamps());
    }

    /**
     * @param commonName the certificate's common name
     * @return a stranger's signing certificate whose chain ends in {@link #timestampRoot()}, with its key
     */
    public SigningCertificates.Issued strangerUnderTimestampRoot(String commonName) {
        return service.signingCertificate(commonName);
    }

    /** @return the root the service's timestamps chain to */
    public static X509Certificate timestampRoot() {
        return LocalTimestampService.root();
    }

    /**
     * A sealer of signing states with the installation's real keys under a fixed test secret, which asks no
     * timestamp service.
     *
     * @param documents       the member documents
     * @param documentService their files
     * @param stations        the stations
     * @return the sealer
     */
    public static SigningStateSealer stateSealer(
            DocumentRepository documents, DocumentService documentService, StationRepository stations) {
        var keys = new SigningKeyRepository();
        var wrap = new SigningKeyWrap(Base64.getEncoder().encodeToString(new byte[32]));
        var timestamps = SealedPdfs.noTimestamps();
        return new SigningStateSealer(
                new SignatureRequestRepository(),
                new SigningEvidenceRepository(),
                documents,
                documentService,
                new SealedDocumentService(documents, new SealedVersionRepository(), documentService),
                new StationSigningKeys(keys, new SigningCertificates(), wrap, stations, STATE_BASE_URL),
                new SigningStateAssembler(stations, timestamps, STATE_BASE_URL, Clock.systemUTC()),
                new PdfSealer(timestamps, new StationKeyRevocations(keys, new RevocationLists(), wrap)),
                mock(SignedCopies.class));
    }

    /** @return an uncompressed one-page PDF, the one the sealing tests seal */
    public static byte[] onePagePdf() throws IOException {
        return SealedPdfs.onePagePdf();
    }

    @Override
    public void close() {
        service.close();
    }
}
