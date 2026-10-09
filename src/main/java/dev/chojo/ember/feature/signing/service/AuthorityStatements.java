/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SigningAuthorityStatement;
import dev.chojo.ember.feature.signing.entity.StatedAuthority;
import dev.chojo.ember.util.Sha256;
import org.jspecify.annotations.Nullable;

import java.security.GeneralSecurityException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The text a station signs when it states its installation's signing authorities to a partner, and the
 * checks a partner runs on what such a statement carries.
 *
 * <p>The text is built by both sides from the statement's fields, never sent as text, so the reader signs
 * off on exactly what it is about to pin:
 *
 * <pre>
 * ember-signing-authorities-v1
 * station &lt;uid of the station making it&gt;
 * recipient &lt;uid of the station it is made to&gt;
 * challenge &lt;the recipient's challenge&gt;
 * issued &lt;ISO 8601 moment&gt;
 * authority &lt;SHA-256 of the certificate&gt; &lt;active|retired&gt; &lt;SHA-256 of the revocation list|none&gt;
 * </pre>
 *
 * <p>with one {@code authority} line per authority, in the statement's order, hashes in lower-case
 * hexadecimal and lines ended by a line feed. Covering the revocation lists by their hash means an older
 * list of the same authority, which would still verify against it, cannot be swapped in to hide a
 * revocation. The first line names the kind of statement and its version; no handshake or pairing payload
 * signed with the same key starts with it.
 */
final class AuthorityStatements {
    /** The first line of every statement's text. */
    static final String KIND = "ember-signing-authorities-v1";

    /** How many random bytes a challenge has. */
    static final int CHALLENGE_BYTES = 32;

    private static final Pattern CHALLENGE = Pattern.compile("[0-9a-f]{" + CHALLENGE_BYTES * 2 + "}");

    private AuthorityStatements() {}

    /**
     * @param challenge a challenge as a partner sent it
     * @return whether it is {@value #CHALLENGE_BYTES} bytes in lower-case hexadecimal, which keeps it on its
     *     line of the text
     */
    static boolean wellFormedChallenge(@Nullable String challenge) {
        return challenge != null && CHALLENGE.matcher(challenge).matches();
    }

    /**
     * @param statement a statement as received
     * @return the text its signature has to cover
     */
    static String text(SigningAuthorityStatement statement) {
        return text(
                statement.stationUid(),
                statement.recipientUid(),
                statement.challenge(),
                statement.issuedAt(),
                statement.authorities());
    }

    /**
     * @param station     the station making the statement
     * @param recipient   the station it is made to
     * @param challenge   the recipient's challenge
     * @param issuedAt    when it is made, ISO 8601
     * @param authorities the authorities it names
     * @return the text to sign
     */
    static String text(
            UUID station, UUID recipient, String challenge, String issuedAt, List<StatedAuthority> authorities) {
        var text = new StringBuilder()
                .append(KIND)
                .append('\n')
                .append("station ")
                .append(station)
                .append('\n')
                .append("recipient ")
                .append(recipient)
                .append('\n')
                .append("challenge ")
                .append(challenge)
                .append('\n')
                .append("issued ")
                .append(issuedAt)
                .append('\n');
        for (var authority : authorities) {
            var list = authority.revocationList();
            text.append("authority ")
                    .append(Sha256.hex(authority.certificate()))
                    .append(authority.active() ? " active " : " retired ")
                    .append(list == null ? "none" : Sha256.hex(list))
                    .append('\n');
        }
        return text.toString();
    }

    /**
     * Reads a certificate a partner stated as an authority.
     *
     * @param der the certificate, DER encoded
     * @return it, when it reads, may issue certificates and is self-signed by its own key
     */
    static Optional<X509Certificate> authorityOf(byte[] der) {
        try {
            var certificate = SigningCertificates.certificateOf(der);
            if (certificate.getBasicConstraints() < 0) return Optional.empty();
            if (!certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
                return Optional.empty();
            }
            certificate.verify(certificate.getPublicKey());
            return Optional.of(certificate);
        } catch (IllegalStateException | GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads a revocation list a partner stated for an authority.
     *
     * @param der       the list, DER encoded
     * @param authority the authority it was stated for
     * @return it, when it reads, was signed by that authority and says when the next one is due
     */
    static Optional<X509CRL> revocationListOf(byte[] der, X509Certificate authority) {
        try {
            var list = RevocationLists.read(der);
            if (!list.getIssuerX500Principal().equals(authority.getSubjectX500Principal())) return Optional.empty();
            list.verify(authority.getPublicKey());
            return list.getNextUpdate() == null ? Optional.empty() : Optional.of(list);
        } catch (IllegalStateException | GeneralSecurityException e) {
            return Optional.empty();
        }
    }
}
