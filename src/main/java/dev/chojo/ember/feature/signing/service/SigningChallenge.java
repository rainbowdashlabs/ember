/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.BatchMembership;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningBatch;
import dev.chojo.ember.feature.signing.entity.SigningRequest;

import java.util.List;

/**
 * The challenge a passkey or security key signs for a signing act: the SHA-256 of the nonce, the content
 * hash, the signer and the statement, followed by what else the act names. It is recomputed when the act
 * completes, and again by anyone checking the evidence later, so the byte layout is fixed here and never
 * changes under this label.
 *
 * <p>Every part is written as a four-byte big-endian length followed by that many bytes, so no two
 * different sets of parts can produce the same input ({@link ChallengeInput}). Text is UTF-8 exactly as
 * stored, without any normalisation; ids are four-byte big-endian signed integers. In this order:
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
 *
 * <p>Several fields confirmed by one proof are bound by the batch layout instead ({@link SigningBatchChallenge});
 * a batch of one field is a single act and keeps this layout, byte for byte as a field signed on its own.
 *
 * <p>The signature picture the act leaves in its field is not part of it: the picture is handed over when
 * the act completes, after the challenge was issued. It is bound to the document only by the station's seal
 * over the sealed version and the evidence attached to it ({@code SigningEvidenceFile.Picture}), never by
 * the signer's passkey or security key.
 */
public final class SigningChallenge {
    /** The label that opens every challenge input and names this layout. */
    public static final String LABEL = "ember-signing-challenge-v1";

    /** The length of a nonce, in bytes. */
    public static final int NONCE_BYTES = 32;

    /** The length of a SHA-256, in bytes. */
    static final int HASH_BYTES = 32;

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
     * The challenge of a batch: this layout for a batch of one field, the batch layout for more.
     *
     * @param nonce the nonce issued for this attempt
     * @param batch the fields the attempt answers
     * @return the challenge for the attempt
     */
    public static byte[] of(byte[] nonce, SigningBatch batch) {
        if (batch.single()) return of(nonce, batch.requests().getFirst());
        return SigningBatchChallenge.of(nonce, batch);
    }

    /**
     * The challenge again from recorded evidence, which is how a reader checks it later: in the batch
     * layout for an act confirmed in a batch, in this one otherwise.
     *
     * @param act the recorded act
     * @return the challenge the act's passkey or security key must have signed
     */
    public static byte[] of(SigningAct act) {
        BatchMembership batch = act.batch();
        if (batch != null) return SigningBatchChallenge.of(act, batch);
        return of(act.nonce(), act.contentSha256(), act.signer(), act.statement(), act.fieldName(), act.entries());
    }

    /**
     * @param act the recorded act
     * @return the name of the layout its challenge was computed in
     */
    public static String layoutOf(SigningAct act) {
        return act.batch() == null ? LABEL : SigningBatchChallenge.LABEL;
    }

    private static byte[] of(
            byte[] nonce,
            byte[] contentSha256,
            Signer signer,
            String statement,
            String fieldName,
            List<SignerEntry> entries) {
        requireNonce(nonce);
        requireHash(contentSha256);
        return new ChallengeInput(LABEL)
                .part(nonce)
                .part(contentSha256)
                .part(signer.accountId())
                .part(statement)
                .signer(signer)
                .part(fieldName)
                .entries(entries)
                .hash();
    }

    static void requireNonce(byte[] nonce) {
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("A nonce has " + NONCE_BYTES + " bytes, not " + nonce.length);
        }
    }

    static void requireHash(byte[] sha256) {
        if (sha256.length != HASH_BYTES) {
            throw new IllegalArgumentException("A content hash has " + HASH_BYTES + " bytes");
        }
    }
}
