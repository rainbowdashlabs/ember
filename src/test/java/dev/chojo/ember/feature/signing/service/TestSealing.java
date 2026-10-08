/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Sealing for tests outside this package: a timestamp service on loopback, a sealer that stamps through
 * it, and the small document the sealing tests seal. Nothing here reaches the internet.
 */
public final class TestSealing implements AutoCloseable {
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

    /** @return an uncompressed one-page PDF, the one the sealing tests seal */
    public static byte[] onePagePdf() throws IOException {
        return SealedPdfs.onePagePdf();
    }

    @Override
    public void close() {
        service.close();
    }
}
