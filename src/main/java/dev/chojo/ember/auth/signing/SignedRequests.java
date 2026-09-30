/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Signs and verifies requests between installations, whatever the protocol.
 *
 * <p>A protocol supplies its {@link SignatureAlgorithm} and its {@link SignedEnvelope}; everything
 * else is the same for all of them: the signature travels as Base64, a signature that cannot be
 * decoded or checked is simply not valid, and a request is only taken within a time window around
 * the moment it says it was issued. What happens after that, remembering the nonce so the request
 * is taken only once, is a {@link ReplayStore}'s job.
 */
public final class SignedRequests {

    private SignedRequests() {}

    /**
     * Signs an envelope.
     *
     * @param algorithm the protocol's algorithm
     * @param key       the signing key
     * @param envelope  what the signature covers
     * @return the Base64 signature
     * @throws IllegalStateException when the key does not fit the algorithm
     */
    public static String sign(SignatureAlgorithm algorithm, PrivateKey key, SignedEnvelope envelope) {
        try {
            var signer = Signature.getInstance(algorithm.jcaName());
            signer.initSign(key);
            signer.update(envelope.bytes());
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign with " + algorithm, e);
        }
    }

    /**
     * Checks a signature over an envelope.
     *
     * @param algorithm the protocol's algorithm
     * @param key       the sender's public key
     * @param envelope  what the signature is supposed to cover
     * @param signature the Base64 signature as received
     * @return true only when the signature decodes and matches
     */
    public static boolean verify(
            SignatureAlgorithm algorithm, PublicKey key, SignedEnvelope envelope, String signature) {
        if (key == null || signature == null) return false;
        try {
            var verifier = Signature.getInstance(algorithm.jcaName());
            verifier.initVerify(key);
            verifier.update(envelope.bytes());
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Whether a request's issue time lies close enough to now to be taken.
     *
     * @param issuedAt when the request says it was issued, may be {@code null}
     * @param now      the receiver's clock
     * @param drift    how far the two may lie apart in either direction
     * @return true when the issue time is present and within the window
     */
    public static boolean withinDrift(Instant issuedAt, Instant now, Duration drift) {
        return issuedAt != null && Duration.between(issuedAt, now).abs().compareTo(drift) <= 0;
    }
}
