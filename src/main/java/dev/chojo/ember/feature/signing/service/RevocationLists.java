/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.RevokedKey;
import jakarta.inject.Singleton;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Builds the X.509 version 2 revocation lists of the installation's authorities (RFC 5280, section 5).
 *
 * <p>A list is signed by the authority that issued the certificates it names, with the algorithm its
 * certificates use, and carries the authority key identifier, so a reader matches it to the authority
 * even after a renewal left two of them with the same name, and a CRL number, so a reader can tell the
 * newer of two lists apart. Every entry carries the date the key was revoked and its reason code.
 * Entries are never dropped once the certificate has expired: a document sealed with the key stays
 * checkable for as long as anyone holds it, and the lists stay small.
 */
@Singleton
public class RevocationLists {
    /**
     * How long a list holds ({@code nextUpdate} after {@code thisUpdate}). A reader treats a revocation
     * as unknown to it for at most this long; a week is the usual period for an authority that issues
     * few certificates, and short enough for a leaked key to stop being trusted within days, while an
     * installation that is unreachable for a few days does not leave readers without a current list.
     */
    public static final Duration VALIDITY = Duration.ofDays(7);

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /**
     * Issues a revocation list.
     *
     * @param authority  the authority's key and certificate
     * @param number     the list's CRL number, above every number the authority used before
     * @param revoked    every key the authority issued that was revoked
     * @param thisUpdate when the list is issued
     * @return the signed list
     */
    public X509CRL issue(
            SigningCertificates.Issued authority, long number, List<RevokedKey> revoked, Instant thisUpdate) {
        var builder = new JcaX509v2CRLBuilder(authority.certificate(), Date.from(thisUpdate))
                .setNextUpdate(Date.from(thisUpdate.plus(VALIDITY)));
        for (var key : revoked) {
            builder.addCRLEntry(
                    new BigInteger(key.serialNumber(), 16),
                    Date.from(key.revokedAt()),
                    key.reason().code());
        }
        try {
            builder.addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.valueOf(number)))
                    .addExtension(
                            Extension.authorityKeyIdentifier,
                            false,
                            new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(authority.certificate()));
            var signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(authority.privateKey());
            return new JcaX509CRLConverter().getCRL(builder.build(signer));
        } catch (GeneralSecurityException | CertIOException | OperatorCreationException e) {
            throw new IllegalStateException("The revocation list could not be signed", e);
        }
    }

    /**
     * @param der a revocation list, DER encoded, as it is stored
     * @return the list
     */
    public static X509CRL read(byte[] der) {
        try {
            return (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(new ByteArrayInputStream(der));
        } catch (CertificateException | CRLException e) {
            throw new IllegalStateException("A stored revocation list cannot be read", e);
        }
    }
}
