/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningRequest;
import dev.chojo.ember.util.Sha256;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * The challenge a passkey or security key signs for a signing act: the SHA-256 of the nonce, the content
 * hash, the signer and the statement, followed by what else the act names. It is recomputed when the act
 * completes, and again by anyone checking the evidence later, so the byte layout is fixed here and never
 * changes under this label.
 *
 * <p>Every part is written as a four-byte big-endian length followed by that many bytes, so no two
 * different sets of parts can produce the same input. Text is UTF-8 exactly as stored, without any
 * normalisation; ids are four-byte big-endian signed integers. In this order:
 *
 * <ol>
 *   <li>the label {@value #LABEL}, as text</li>
 *   <li>the nonce, thirty-two bytes</li>
 *   <li>the raw SHA-256 of the frozen content, thirty-two bytes</li>
 *   <li>the account id whose step-up confirms the act</li>
 *   <li>the statement shown to the signer</li>
 *   <li>the capacity: {@code account-holder}, {@code guardian} or {@code member-through-account}, as text</li>
 *   <li>the member id, or a part of length zero when the account holder signs for themselves</li>
 *   <li>the name of the signature field</li>
 *   <li>the number of entries the signer typed, as a bare four-byte big-endian integer, then for each
 *       entry in its order its field name and its value as two parts</li>
 * </ol>
 *
 * <p>The challenge is the SHA-256 of all of that, thirty-two bytes.
 */
public final class SigningChallenge {
    /** The label that opens every challenge input and names this layout. */
    public static final String LABEL = "ember-signing-challenge-v1";

    /** The length of a nonce, in bytes. */
    public static final int NONCE_BYTES = 32;

    private static final int CONTENT_HASH_BYTES = 32;

    private SigningChallenge() {}

    /**
     * @param nonce   the nonce issued for this attempt
     * @param request the request the attempt answers
     * @return the challenge for the attempt
     */
    public static byte[] of(byte[] nonce, SigningRequest request) {
        return of(
                nonce,
                request.contentSha256(),
                request.signer(),
                request.statement(),
                request.fieldName(),
                request.entries());
    }

    /**
     * The challenge again from recorded evidence, which is how a reader checks it later.
     *
     * @param act the recorded act
     * @return the challenge the act's passkey or security key must have signed
     */
    public static byte[] of(SigningAct act) {
        return of(act.nonce(), act.contentSha256(), act.signer(), act.statement(), act.fieldName(), act.entries());
    }

    private static byte[] of(
            byte[] nonce,
            byte[] contentSha256,
            Signer signer,
            String statement,
            String fieldName,
            List<SignerEntry> entries) {
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("A nonce has " + NONCE_BYTES + " bytes, not " + nonce.length);
        }
        if (contentSha256.length != CONTENT_HASH_BYTES) {
            throw new IllegalArgumentException("A content hash has " + CONTENT_HASH_BYTES + " bytes");
        }
        MessageDigest digest = Sha256.digest();
        part(digest, LABEL);
        part(digest, nonce);
        part(digest, contentSha256);
        part(digest, integer(signer.accountId()));
        part(digest, statement);
        part(digest, capacity(signer));
        Integer memberId = signer.memberId();
        part(digest, memberId == null ? new byte[0] : integer(memberId));
        part(digest, fieldName);
        digest.update(integer(entries.size()));
        for (SignerEntry entry : entries) {
            part(digest, entry.field());
            part(digest, entry.value());
        }
        return digest.digest();
    }

    private static String capacity(Signer signer) {
        return switch (signer.capacity()) {
            case ACCOUNT_HOLDER -> "account-holder";
            case GUARDIAN -> "guardian";
            case MEMBER_THROUGH_ACCOUNT -> "member-through-account";
        };
    }

    private static void part(MessageDigest digest, String text) {
        part(digest, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void part(MessageDigest digest, byte[] bytes) {
        digest.update(integer(bytes.length));
        digest.update(bytes);
    }

    private static byte[] integer(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }
}
