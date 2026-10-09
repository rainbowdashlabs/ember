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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The challenge one passkey or security key signs for several fields at once, across documents and across
 * the members in the signer's care. Like the single layout ({@link SigningChallenge}) it is fixed here and
 * never changes under its label, and it is written the same way ({@link ChallengeInput}).
 *
 * <p>It is built in two steps. Each field is first reduced to its item digest, the SHA-256 of:
 *
 * <ol>
 *   <li>the label {@value #ITEM_LABEL}, as text</li>
 *   <li>the request uid, sixteen bytes</li>
 *   <li>the raw SHA-256 of the frozen content, thirty-two bytes</li>
 *   <li>the statement shown to the signer</li>
 *   <li>the capacity: {@code account-holder}, {@code guardian} or {@code member-through-account}, as text</li>
 *   <li>the member id, or a part of length zero when the account holder signs for themselves</li>
 *   <li>the name of the signature field</li>
 *   <li>the number of entries as a bare four-byte integer, then each entry's field name and value</li>
 * </ol>
 *
 * <p>The challenge is then the SHA-256 of:
 *
 * <ol>
 *   <li>the label {@value #LABEL}, as text</li>
 *   <li>the nonce, thirty-two bytes</li>
 *   <li>the batch uid, sixteen bytes</li>
 *   <li>the account id whose step-up confirms every field</li>
 *   <li>the number of fields as a bare four-byte integer, then each field's item digest as a part, in the
 *       order the fields were signed</li>
 * </ol>
 *
 * <p>The two steps are what lets the evidence of one document recompute the challenge without carrying what
 * the other documents bound: it holds its own act in full and the other fields as digests only
 * ({@link BatchMembership}). Changing anything of its own act changes its digest and with it the challenge;
 * a digest cannot be changed without changing the challenge either.
 */
public final class SigningBatchChallenge {
    /** The label that opens a batch challenge and names this layout. */
    public static final String LABEL = "ember-signing-batch-v1";

    /** The label that opens the digest of each field of a batch. */
    public static final String ITEM_LABEL = "ember-signing-batch-item-v1";

    private SigningBatchChallenge() {}

    /**
     * @param nonce the nonce issued for the attempt
     * @param batch the fields, two or more
     * @return the challenge the passkey or security key signs
     */
    static byte[] of(byte[] nonce, SigningBatch batch) {
        return of(nonce, batch.uid(), batch.accountId(), digestsOf(batch));
    }

    /**
     * The challenge again from one act of a batch: its own digest recomputed from the act at its position,
     * the others as its evidence names them.
     *
     * @param act   the recorded act
     * @param batch the batch the act names
     * @return the challenge the batch's passkey or security key must have signed
     */
    static byte[] of(SigningAct act, BatchMembership batch) {
        var digests = new ArrayList<byte[]>(batch.items().size());
        for (int position = 0; position < batch.items().size(); position++) {
            digests.add(
                    position == batch.position()
                            ? itemDigest(act)
                            : batch.items().get(position).digest());
        }
        return of(act.nonce(), batch.uid(), act.signer().accountId(), digests);
    }

    /**
     * @param batch the fields
     * @return the item digest of each field, in the batch's order
     */
    public static List<byte[]> digestsOf(SigningBatch batch) {
        return batch.requests().stream().map(SigningBatchChallenge::itemDigest).toList();
    }

    /**
     * @param request one field of a batch
     * @return its item digest
     */
    static byte[] itemDigest(SigningRequest request) {
        return itemDigest(
                request.requestUid(),
                request.contentSha256(),
                request.statement(),
                request.signer(),
                request.fieldName(),
                request.entries());
    }

    /**
     * @param act one act of a batch
     * @return its item digest, recomputed from what it recorded
     */
    public static byte[] itemDigest(SigningAct act) {
        return itemDigest(
                act.requestUid(), act.contentSha256(), act.statement(), act.signer(), act.fieldName(), act.entries());
    }

    private static byte[] itemDigest(
            UUID requestUid,
            byte[] contentSha256,
            String statement,
            Signer signer,
            String fieldName,
            List<SignerEntry> entries) {
        SigningChallenge.requireHash(contentSha256);
        return new ChallengeInput(ITEM_LABEL)
                .part(requestUid)
                .part(contentSha256)
                .part(statement)
                .signer(signer)
                .part(fieldName)
                .entries(entries)
                .hash();
    }

    private static byte[] of(byte[] nonce, UUID batchUid, int accountId, List<byte[]> digests) {
        SigningChallenge.requireNonce(nonce);
        var input = new ChallengeInput(LABEL)
                .part(nonce)
                .part(batchUid)
                .part(accountId)
                .count(digests.size());
        for (byte[] digest : digests) {
            SigningChallenge.requireHash(digest);
            input.part(digest);
        }
        return input.hash();
    }
}
