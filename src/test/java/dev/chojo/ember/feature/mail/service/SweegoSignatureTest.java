/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proof that a delivery report came from Sweego.
 *
 * <p>The signature covers the body exactly as it arrived, so the test signs the same way Sweego
 * documents it and then changes one thing at a time: the body, the timestamp, the id, the secret.
 * Each of those has to break the proof, or the check is worth nothing.
 */
class SweegoSignatureTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("a-signing-secret".getBytes(StandardCharsets.UTF_8));
    private static final String ID = "237e3736c687425d9ea8665216bcfe8a";
    private static final String TIMESTAMP = "1769696506";
    private static final String BODY = "{\"event_type\":\"soft-bounce\",\"recipient\":\"someone@example.test\"}";

    private static String sign(String id, String timestamp, String body, String secret) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"));
        return Base64.getEncoder()
                .encodeToString(mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aReportSignedWithTheSecretIsAccepted() throws Exception {
        assertTrue(SweegoSignature.matches(ID, TIMESTAMP, sign(ID, TIMESTAMP, BODY, SECRET), BODY, SECRET));
    }

    /**
     * The point of signing: a body altered on the way no longer matches what was signed.
     */
    @Test
    void aChangedBodyIsRefused() throws Exception {
        String signature = sign(ID, TIMESTAMP, BODY, SECRET);
        String tampered = BODY.replace("soft-bounce", "delivered");

        assertFalse(SweegoSignature.matches(ID, TIMESTAMP, signature, tampered, SECRET));
    }

    @Test
    void aSignatureFromAnotherCallIsRefused() throws Exception {
        String signature = sign(ID, TIMESTAMP, BODY, SECRET);

        assertFalse(SweegoSignature.matches("another-id", TIMESTAMP, signature, BODY, SECRET));
        assertFalse(SweegoSignature.matches(ID, "1769696507", signature, BODY, SECRET));
    }

    @Test
    void anotherSecretIsRefused() throws Exception {
        String elsewhere = Base64.getEncoder().encodeToString("a-different-secret".getBytes(StandardCharsets.UTF_8));

        assertFalse(SweegoSignature.matches(ID, TIMESTAMP, sign(ID, TIMESTAMP, BODY, elsewhere), BODY, SECRET));
    }

    /**
     * Nothing missing may pass for a match, and a secret that is not base64 is a configuration
     * mistake rather than a reason to trust the caller.
     */
    @Test
    void anythingIncompleteIsRefused() throws Exception {
        String signature = sign(ID, TIMESTAMP, BODY, SECRET);

        assertFalse(SweegoSignature.matches(null, TIMESTAMP, signature, BODY, SECRET));
        assertFalse(SweegoSignature.matches(ID, null, signature, BODY, SECRET));
        assertFalse(SweegoSignature.matches(ID, TIMESTAMP, null, BODY, SECRET));
        assertFalse(SweegoSignature.matches(ID, TIMESTAMP, signature, null, SECRET));
        assertFalse(SweegoSignature.matches(ID, TIMESTAMP, signature, BODY, ""));
        assertFalse(SweegoSignature.matches(ID, TIMESTAMP, signature, BODY, "not base64 at all!"));
    }
}
