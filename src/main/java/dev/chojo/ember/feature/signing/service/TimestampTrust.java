/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampToken;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Checks that a timestamp comes from the service it was asked of: the token is signed by a timestamp
 * certificate, and that certificate chains to the root pinned for the service.
 *
 * <p>Everything is checked offline. The token's signature and its reference to the signing certificate
 * are verified, the certificate must carry the timestamping key purpose, marked critical, and be valid
 * when the timestamp was made. The chain is built from the certificates the token carries, up to the
 * pinned root, as of the timestamp's time; no issuer is downloaded and no revocation is consulted here,
 * since the seal fetches the chain's revocation data in its next step.
 */
final class TimestampTrust {
    private TimestampTrust() {}

    /**
     * Refuses a timestamp that does not chain to the pinned root, and says when it stops proving anything on
     * its own: the earliest end of validity along the chain it was checked over, from its signing certificate
     * to the pinned root. Certificates the token carries beside that chain do not count. Before then a later
     * timestamp has to cover it.
     *
     * @param token the timestamp token, a DER encoded CMS signed data
     * @param root  the root pinned for the service that answered
     * @return the earliest end of validity along the chain
     * @throws UntrustedTimestampException when the token is malformed, its signature does not hold, or its
     *                                     signing certificate does not chain to the root
     */
    static Instant trustedUntil(byte[] token, X509Certificate root) {
        try {
            var timestamp = new TimeStampToken(new CMSSignedData(token));
            var signer = signerOf(timestamp);
            timestamp.validate(new JcaSimpleSignerInfoVerifierBuilder().build(signer));
            var chain = requireChain(certificate(signer), certificatesOf(timestamp), root, timestamp);
            return earliestEnd(chain, root);
        } catch (TSPException | CMSException | IOException | OperatorCreationException | GeneralSecurityException e) {
            throw new UntrustedTimestampException(
                    "The timestamp does not chain to the pinned root: " + e.getMessage(), e);
        }
    }

    private static Instant earliestEnd(List<? extends Certificate> chain, X509Certificate root) {
        var earliest = root.getNotAfter().toInstant();
        for (var certificate : chain) {
            var end = ((X509Certificate) certificate).getNotAfter().toInstant();
            if (end.isBefore(earliest)) earliest = end;
        }
        return earliest;
    }

    /** @return the validated path from the signing certificate up to, not including, the root */
    private static List<? extends Certificate> requireChain(
            X509Certificate signer, List<X509Certificate> certificates, X509Certificate root, TimeStampToken timestamp)
            throws GeneralSecurityException {
        if (signer.equals(root)) return List.of();
        var target = new X509CertSelector();
        target.setCertificate(signer);
        var parameters = new PKIXBuilderParameters(Set.of(new TrustAnchor(root, null)), target);
        parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(certificates)));
        parameters.setRevocationEnabled(false);
        parameters.setDate(timestamp.getTimeStampInfo().getGenTime());
        return CertPathBuilder.getInstance("PKIX")
                .build(parameters)
                .getCertPath()
                .getCertificates();
    }

    private static X509CertificateHolder signerOf(TimeStampToken timestamp) throws TSPException {
        var signer = timestamp.getSID();
        return holdersOf(timestamp).stream()
                .filter(signer::match)
                .findFirst()
                .orElseThrow(() -> new TSPException("The timestamp does not carry its signing certificate"));
    }

    private static List<X509Certificate> certificatesOf(TimeStampToken timestamp) throws GeneralSecurityException {
        var converted = new ArrayList<X509Certificate>();
        for (var holder : holdersOf(timestamp)) converted.add(certificate(holder));
        return converted;
    }

    private static Collection<X509CertificateHolder> holdersOf(TimeStampToken timestamp) {
        return timestamp.getCertificates().getMatches(null);
    }

    private static X509Certificate certificate(X509CertificateHolder holder) throws GeneralSecurityException {
        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}
