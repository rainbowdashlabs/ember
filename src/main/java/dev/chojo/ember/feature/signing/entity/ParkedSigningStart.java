/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A started signing act as the server keeps it until the act completes: everything the completion needs
 * besides the documents themselves, which are read again from where they are filed. The browser only ever
 * holds the token it is kept under, so nothing it sends back can change what is signed, by whom or with
 * which values.
 *
 * <p>An act covers one field or several fields confirmed together, in the order the signer chose.
 *
 * <p>The arrays are handed over as they are, without a copy.
 *
 * @param stationId      the station the act happens at
 * @param accountId      the account whose step-up confirms the act
 * @param batchUid       the batch the fields are confirmed in
 * @param nonce          the nonce the provider issued
 * @param challenge      the challenge the provider computed from the nonce and the fields
 * @param acceptedProofs the proofs the signer may confirm with
 * @param items          the fields, in the order they are signed
 */
public record ParkedSigningStart(
        int stationId,
        int accountId,
        UUID batchUid,
        byte[] nonce,
        byte[] challenge,
        Set<StepUpProof> acceptedProofs,
        List<Item> items) {
    /** Copies the proofs and the items, so they cannot change after the fact. */
    public ParkedSigningStart {
        acceptedProofs = Set.copyOf(acceptedProofs);
        items = List.copyOf(items);
    }

    /**
     * @param stationId the station the act happens at
     * @param batch     the fields as the provider was asked to start them
     * @param fieldIds  the row id of each field, in the batch's order
     * @param started   what the provider issued
     * @return the start as it is kept
     */
    public static ParkedSigningStart of(
            int stationId, SigningBatch batch, List<Integer> fieldIds, SigningStart.InEmber started) {
        var requests = batch.requests();
        if (requests.size() != fieldIds.size()) throw new IllegalArgumentException("One field id per field");
        var items = new ArrayList<Item>(requests.size());
        for (int position = 0; position < requests.size(); position++) {
            var request = requests.get(position);
            items.add(new Item(
                    fieldIds.get(position),
                    request.requestUid(),
                    request.fieldName(),
                    request.signer().capacity(),
                    request.signer().memberId(),
                    request.entries()));
        }
        return new ParkedSigningStart(
                stationId,
                batch.accountId(),
                batch.uid(),
                started.nonce(),
                started.challenge(),
                started.acceptedProofs(),
                items);
    }

    /**
     * @param item one of the fields
     * @return who signs it, and through whose account
     */
    public Signer signer(Item item) {
        return new Signer(item.capacity(), accountId, item.memberId());
    }

    /**
     * One field of a started act.
     *
     * @param fieldId    the signature field the act fills
     * @param requestUid the request the field belongs to
     * @param fieldName  the field's name in the document
     * @param capacity   in what capacity the signer signs it
     * @param memberId   the member the field concerns, or null where the account holder signs for themselves
     * @param entries    what the signer typed into fields of their own
     */
    public record Item(
            int fieldId,
            UUID requestUid,
            String fieldName,
            SignerCapacity capacity,
            @Nullable Integer memberId,
            List<SignerEntry> entries) {
        /** Copies the entries, so they cannot change after the fact. */
        public Item {
            entries = List.copyOf(entries);
        }
    }
}
