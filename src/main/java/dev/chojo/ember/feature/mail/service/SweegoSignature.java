/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Proof that a delivery report really came from Sweego.
 *
 * <p>Sweego signs every webhook call, which is more than most relays do: the call carries an id, a
 * timestamp and an HMAC-SHA256 over {@code id.timestamp.body}, keyed with the secret from the
 * webhook settings. Checking it means a report is trusted because it is provably theirs, not merely
 * because the caller knew an address.
 *
 * <p>The body has to be the bytes as received. Parsing the JSON and writing it out again would
 * reorder fields or change whitespace, and the signature would no longer match.
 */
public final class SweegoSignature {
    private static final Logger log = LoggerFactory.getLogger(SweegoSignature.class);

    private static final String ALGORITHM = "HmacSHA256";

    private SweegoSignature() {}

    /**
     * Whether a call carries a signature that this secret can produce.
     *
     * @param webhookId  the {@code webhook-id} header
     * @param timestamp  the {@code webhook-timestamp} header
     * @param signature  the {@code webhook-signature} header, base64
     * @param rawBody    the body exactly as it arrived
     * @param secret     the webhook secret from Sweego, itself base64
     * @return whether the signature belongs to this body
     */
    public static boolean matches(String webhookId, String timestamp, String signature, String rawBody, String secret) {
        if (webhookId == null || timestamp == null || signature == null || rawBody == null) return false;
        if (secret == null || secret.isBlank()) return false;
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), ALGORITHM));
            byte[] digest = mac.doFinal((webhookId + "." + timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(digest);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            log.warn("The configured Sweego webhook secret is not valid base64");
            return false;
        } catch (Exception e) {
            log.error("Failed to check a Sweego webhook signature", e);
            return false;
        }
    }
}
