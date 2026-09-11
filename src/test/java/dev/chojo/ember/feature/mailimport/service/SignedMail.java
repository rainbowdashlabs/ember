/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import org.apache.james.jdkim.DKIMSigner;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

/**
 * Mail that is really signed, for the stories about whether a signature holds.
 *
 * <p>One key pair for the whole run, published for every domain that is asked about. Serving the same
 * key to everybody is what makes the story about alignment tellable: a message signed by a domain that
 * is not the sender's verifies perfectly and still has to be refused, which is the only thing standing
 * between this feature and a stranger who signs their own forgery.
 */
final class SignedMail {

    private static final KeyPair KEYS = keyPair();
    private static final String SELECTOR = "post";

    private SignedMail() {}

    /** The key every domain in these stories publishes, so that a signature by any of them verifies. */
    static PublicKeyRecordRetriever publishedKeys() {
        String record = "v=DKIM1; k=rsa; p="
                + Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
        return (method, selector, domain) -> List.of(record);
    }

    /** A retriever for a domain that publishes nothing, which is what an unknown selector looks like. */
    static PublicKeyRecordRetriever noPublishedKeys() {
        return (method, selector, domain) -> List.of();
    }

    /**
     * The same message with a signature of that domain in front of it.
     *
     * @param domain what goes into {@code d=}, which need not be the domain the message says it is from
     * @param source the message as it stands, which the signature is taken over byte for byte
     */
    static byte[] signedBy(String domain, byte[] source) throws Exception {
        String template = "v=1; a=rsa-sha256; c=relaxed/relaxed; s=%s; d=%s; h=from:subject; bh=; b=;"
                .formatted(SELECTOR, domain);
        String field = new DKIMSigner(template, KEYS.getPrivate()).sign(new ByteArrayInputStream(source));

        var signed = new ByteArrayOutputStream();
        signed.writeBytes((field + "\r\n").getBytes(StandardCharsets.US_ASCII));
        signed.writeBytes(source);
        return signed.toByteArray();
    }

    private static KeyPair keyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("No RSA key pair for the signing stories", e);
        }
    }
}
