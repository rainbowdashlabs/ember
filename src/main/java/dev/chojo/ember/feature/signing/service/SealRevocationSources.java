/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import eu.europa.esig.dss.alert.SilentOnStatusAlert;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.revocation.crl.CRL;
import eu.europa.esig.dss.model.x509.revocation.ocsp.OCSP;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.revocation.RevocationSource;
import eu.europa.esig.dss.spi.x509.revocation.RevocationToken;
import eu.europa.esig.dss.spi.x509.revocation.crl.ExternalResourcesCRLSource;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Optional;

/**
 * Where the revocation data a seal embeds comes from.
 *
 * <p>A certificate issued by one of the installation's own authorities is checked against that
 * authority's current revocation list, read from the database ({@link StationKeyRevocations}); its
 * address in the certificate is never fetched and no status responder is asked, so the seal's own
 * chain causes no network call. Every other certificate belongs to a timestamp service, and its
 * revocation data is fetched from the addresses it names through the seal's
 * {@link TimestampServices.Round}, inside the same budget as the timestamp. Without a round, as when
 * timestamps are off, only the installation's own lists are consulted.
 *
 * <p>Revocation is checked even though the verifier trusts no certificate: the seal is made without
 * the trusted lists, so every chain is untrusted at that moment, and DSS would otherwise collect no
 * revocation data at all.
 *
 * <p>Revocation lists are issued periodically, the installation's own once a day and a timestamp
 * service's on its own schedule, so the list a seal embeds was nearly always issued shortly before
 * the seal's timestamp. DSS warns about that on every seal by default; those two warnings are
 * silenced, since the list is current until its next update, which lies after the seal, and DSS
 * validates such a seal as passed.
 */
final class SealRevocationSources {
    private SealRevocationSources() {}

    /**
     * Sets the revocation sources of a seal on a verifier.
     *
     * @param verifier the verifier the seal is made with
     * @param own      the installation's own revocation lists
     * @param round    the seal's outside calls, or null when nothing may be fetched
     */
    static void configure(
            CommonCertificateVerifier verifier, StationKeyRevocations own, TimestampServices.@Nullable Round round) {
        verifier.setCheckRevocationForUntrustedChains(true);
        verifier.setAlertOnNoRevocationAfterBestSignatureTime(new SilentOnStatusAlert());
        verifier.setAlertOnUncoveredPOE(new SilentOnStatusAlert());
        verifier.setCrlSource(new Lists(own, round == null ? null : round.revocationLists()));
        verifier.setOcspSource(new Status(own, round == null ? null : round.revocationStatus()));
    }

    private static Optional<byte[]> ownList(StationKeyRevocations own, CertificateToken issuer) {
        return own.revocationList(SigningCertificates.serialOf(issuer.getCertificate()));
    }

    /**
     * Revocation lists: the installation's own for the certificates its authorities issued, fetched
     * for every other one.
     */
    private static final class Lists implements RevocationSource<CRL> {
        @Serial
        private static final long serialVersionUID = 1L;

        private final StationKeyRevocations own;
        private final @Nullable RevocationSource<CRL> online;

        private Lists(StationKeyRevocations own, @Nullable RevocationSource<CRL> online) {
            this.own = own;
            this.online = online;
        }

        @Override
        public @Nullable RevocationToken<CRL> getRevocationToken(
                CertificateToken certificate, CertificateToken issuer) {
            var list = ownList(own, issuer);
            if (list.isPresent()) {
                return new ExternalResourcesCRLSource(new InMemoryDocument(list.get()))
                        .getRevocationToken(certificate, issuer);
            }
            return online == null ? null : online.getRevocationToken(certificate, issuer);
        }
    }

    /**
     * Status responders: never asked about a certificate the installation's authorities issued, asked
     * about every other one.
     */
    private static final class Status implements RevocationSource<OCSP> {
        @Serial
        private static final long serialVersionUID = 1L;

        private final StationKeyRevocations own;
        private final @Nullable RevocationSource<OCSP> online;

        private Status(StationKeyRevocations own, @Nullable RevocationSource<OCSP> online) {
            this.own = own;
            this.online = online;
        }

        @Override
        public @Nullable RevocationToken<OCSP> getRevocationToken(
                CertificateToken certificate, CertificateToken issuer) {
            if (online == null || ownList(own, issuer).isPresent()) return null;
            return online.getRevocationToken(certificate, issuer);
        }
    }
}
