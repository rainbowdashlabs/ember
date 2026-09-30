/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Proof that a delivery report really came from Sweego.
 *
 * <p>Sweego signs every webhook call the Standard Webhooks way: the call carries a
 * {@code webhook-id}, a {@code webhook-timestamp} in Unix seconds and a {@code webhook-signature}
 * holding an HMAC-SHA256 over {@code id.timestamp.body}, keyed with the secret from the webhook
 * settings. Checking it means a report is trusted because it is provably theirs, not merely
 * because the caller knew an address.
 *
 * <p>The signature header is a space-separated list of entries. Each is {@code v1,<base64>}; a
 * bare base64 value is read as a {@code v1} entry as well, so a sender that leaves the version out
 * still verifies. Entries of any other version are skipped, and one matching entry is enough, which
 * is how a sender rotating its secret signs with both. Every comparison is constant time.
 *
 * <p>A timestamp further than {@link #TOLERANCE} from now is refused whatever the signature says:
 * the timestamp is signed, so this is what keeps a captured call from being replayed later.
 *
 * <p>The body has to be the bytes as received. Parsing the JSON and writing it out again would
 * reorder fields or change whitespace, and the signature would no longer match. The secret may
 * carry the {@code whsec_} prefix Standard Webhooks senders show; it is stripped before decoding.
 */
public final class SweegoSignature {
    private static final Logger log = LoggerFactory.getLogger(SweegoSignature.class);

    /** How far a signed timestamp may lie from the receiving clock, in either direction. */
    public static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String VERSION_PREFIX = "v1,";
    private static final String SECRET_PREFIX = "whsec_";

    private SweegoSignature() {}

    /** What checking one call against the secret found. */
    public enum Verdict {
        /** Signed with this secret, within the tolerance. */
        VALID,
        /** A header or the body is missing, so there is nothing to check. */
        UNSIGNED,
        /** The timestamp is not a number, or lies outside the tolerance. */
        STALE,
        /** No signature entry was produced by this secret for this body. */
        MISMATCH,
        /** The configured secret is not usable. */
        BAD_SECRET
    }

    /**
     * Checks one call against the secret.
     *
     * @param webhookId the {@code webhook-id} header
     * @param timestamp the {@code webhook-timestamp} header, Unix seconds
     * @param signature the {@code webhook-signature} header
     * @param rawBody   the body exactly as it arrived
     * @param secret    the webhook secret from Sweego, base64 with an optional {@code whsec_} prefix
     * @param now       the receiving clock
     * @return what the check found; only {@link Verdict#VALID} means the call may be trusted
     */
    public static Verdict verify(
            String webhookId, String timestamp, String signature, String rawBody, String secret, Instant now) {
        if (webhookId == null || timestamp == null || signature == null || rawBody == null) return Verdict.UNSIGNED;
        if (!withinTolerance(timestamp, now)) return Verdict.STALE;
        byte[] key = decodeSecret(secret);
        if (key == null) return Verdict.BAD_SECRET;
        byte[] expected = sign(key, webhookId + "." + timestamp + "." + rawBody);
        for (String entry : signature.trim().split("\\s+")) {
            byte[] presented = decodeEntry(entry);
            if (presented != null && MessageDigest.isEqual(expected, presented)) return Verdict.VALID;
        }
        return Verdict.MISMATCH;
    }

    private static boolean withinTolerance(String timestamp, Instant now) {
        try {
            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
            return Duration.between(signedAt, now).abs().compareTo(TOLERANCE) <= 0;
        } catch (NumberFormatException | DateTimeException e) {
            return false;
        }
    }

    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) return null;
        String encoded = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        try {
            byte[] key = Base64.getDecoder().decode(encoded.trim());
            return key.length == 0 ? null : key;
        } catch (IllegalArgumentException e) {
            log.warn("The configured Sweego webhook secret is not valid base64");
            return null;
        }
    }

    private static byte[] decodeEntry(String entry) {
        String encoded;
        if (entry.startsWith(VERSION_PREFIX)) {
            encoded = entry.substring(VERSION_PREFIX.length());
        } else if (entry.contains(",")) {
            return null;
        } else {
            encoded = entry;
        }
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] sign(byte[] key, String content) {
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
