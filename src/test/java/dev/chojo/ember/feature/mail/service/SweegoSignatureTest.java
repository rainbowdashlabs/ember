/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.mail.service.SweegoSignature.Verdict;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proof that a delivery report came from Sweego.
 *
 * <p>The signature covers the body exactly as it arrived, so the test signs the same way Sweego
 * documents it and then changes one thing at a time: the body, the timestamp, the id, the secret,
 * the age of the call. Each of those has to break the proof, or the check is worth nothing.
 */
class SweegoSignatureTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("a-signing-secret".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("a-different-secret".getBytes(StandardCharsets.UTF_8));
    private static final String ID = "237e3736c687425d9ea8665216bcfe8a";
    private static final String TIMESTAMP = "1769696506";
    private static final Instant NOW =
            Instant.ofEpochSecond(Long.parseLong(TIMESTAMP)).plusSeconds(30);
    private static final String BODY = "{\"event_type\":\"soft-bounce\",\"recipient\":\"someone@example.test\"}";

    private static String sign(String id, String timestamp, String body, String secret) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"));
        return Base64.getEncoder()
                .encodeToString(mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private static Verdict verify(String signature) {
        return SweegoSignature.verify(ID, TIMESTAMP, signature, BODY, SECRET, NOW);
    }

    @Test
    void aVersionedSignatureIsAccepted() throws Exception {
        assertEquals(Verdict.VALID, verify("v1," + sign(ID, TIMESTAMP, BODY, SECRET)));
    }

    @Test
    void aBareSignatureIsAccepted() throws Exception {
        assertEquals(Verdict.VALID, verify(sign(ID, TIMESTAMP, BODY, SECRET)));
    }

    @Test
    void aSecretWithTheStandardPrefixIsAccepted() throws Exception {
        String signature = "v1," + sign(ID, TIMESTAMP, BODY, SECRET);

        assertEquals(Verdict.VALID, SweegoSignature.verify(ID, TIMESTAMP, signature, BODY, "whsec_" + SECRET, NOW));
    }

    /**
     * A sender rotating its secret signs with the old and the new one; one matching entry is
     * enough, and entries of a version this check does not know are skipped rather than failing it.
     */
    @Test
    void oneMatchingEntryAmongSeveralIsEnough() throws Exception {
        String header = "v2,c29tZXRoaW5n v1," + sign(ID, TIMESTAMP, BODY, OTHER_SECRET) + " v1,"
                + sign(ID, TIMESTAMP, BODY, SECRET);

        assertEquals(Verdict.VALID, verify(header));
    }

    @Test
    void severalEntriesWithoutAMatchAreRefused() throws Exception {
        String header = "v1," + sign(ID, TIMESTAMP, BODY, OTHER_SECRET) + " v1,bm90LWl0";

        assertEquals(Verdict.MISMATCH, verify(header));
    }

    /**
     * The point of signing: a body altered on the way no longer matches what was signed.
     */
    @Test
    void aChangedBodyIsRefused() throws Exception {
        String signature = "v1," + sign(ID, TIMESTAMP, BODY, SECRET);
        String tampered = BODY.replace("soft-bounce", "delivered");

        assertEquals(Verdict.MISMATCH, SweegoSignature.verify(ID, TIMESTAMP, signature, tampered, SECRET, NOW));
    }

    @Test
    void aSignatureFromAnotherCallIsRefused() throws Exception {
        String signature = "v1," + sign(ID, TIMESTAMP, BODY, SECRET);

        assertEquals(Verdict.MISMATCH, SweegoSignature.verify("another-id", TIMESTAMP, signature, BODY, SECRET, NOW));
        assertEquals(Verdict.MISMATCH, SweegoSignature.verify(ID, "1769696507", signature, BODY, SECRET, NOW));
    }

    @Test
    void anotherSecretIsRefused() throws Exception {
        assertEquals(Verdict.MISMATCH, verify("v1," + sign(ID, TIMESTAMP, BODY, OTHER_SECRET)));
    }

    /**
     * A correctly signed call captured once and sent again later carries its old timestamp, and
     * the timestamp is part of what was signed, so it cannot be refreshed without the secret.
     */
    @Test
    void aReplayOutsideTheToleranceIsRefused() throws Exception {
        String signature = "v1," + sign(ID, TIMESTAMP, BODY, SECRET);
        Instant signedAt = Instant.ofEpochSecond(Long.parseLong(TIMESTAMP));

        assertEquals(
                Verdict.STALE,
                SweegoSignature.verify(
                        ID,
                        TIMESTAMP,
                        signature,
                        BODY,
                        SECRET,
                        signedAt.plus(Duration.ofMinutes(5).plusSeconds(1))));
        assertEquals(
                Verdict.STALE,
                SweegoSignature.verify(
                        ID,
                        TIMESTAMP,
                        signature,
                        BODY,
                        SECRET,
                        signedAt.minus(Duration.ofMinutes(5).plusSeconds(1))));
        assertEquals(
                Verdict.VALID,
                SweegoSignature.verify(ID, TIMESTAMP, signature, BODY, SECRET, signedAt.plus(Duration.ofMinutes(5))));
    }

    @Test
    void aTimestampThatIsNotANumberIsRefused() throws Exception {
        String signature = "v1," + sign(ID, "soon", BODY, SECRET);

        assertEquals(Verdict.STALE, SweegoSignature.verify(ID, "soon", signature, BODY, SECRET, NOW));
    }

    /**
     * Nothing missing may pass for a match, and a secret that is not base64 is a configuration
     * mistake rather than a reason to trust the caller.
     */
    @Test
    void anythingIncompleteIsRefused() throws Exception {
        String signature = "v1," + sign(ID, TIMESTAMP, BODY, SECRET);

        assertEquals(Verdict.UNSIGNED, SweegoSignature.verify(null, TIMESTAMP, signature, BODY, SECRET, NOW));
        assertEquals(Verdict.UNSIGNED, SweegoSignature.verify(ID, null, signature, BODY, SECRET, NOW));
        assertEquals(Verdict.UNSIGNED, SweegoSignature.verify(ID, TIMESTAMP, null, BODY, SECRET, NOW));
        assertEquals(Verdict.UNSIGNED, SweegoSignature.verify(ID, TIMESTAMP, signature, null, SECRET, NOW));
        assertEquals(Verdict.BAD_SECRET, SweegoSignature.verify(ID, TIMESTAMP, signature, BODY, "", NOW));
        assertEquals(
                Verdict.BAD_SECRET, SweegoSignature.verify(ID, TIMESTAMP, signature, BODY, "not base64 at all!", NOW));
    }
}
