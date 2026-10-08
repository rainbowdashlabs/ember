/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A started signing act as the server keeps it until the act completes: everything the completion needs
 * besides the document itself, which is read again from where it is filed. The browser only ever holds
 * the token it is kept under, so nothing it sends back can change what is signed, by whom or with which
 * values.
 *
 * <p>The arrays are handed over as they are, without a copy.
 *
 * @param stationId      the station the act happens at
 * @param fieldId        the signature field the act fills
 * @param requestUid     the request the field belongs to
 * @param fieldName      the field's name in the document
 * @param capacity       in what capacity the signer signs
 * @param accountId      the account whose step-up confirms the act
 * @param memberId       the member the act concerns, or null where the account holder signs for themselves
 * @param nonce          the nonce the provider issued
 * @param challenge      the challenge the provider computed from the nonce and the request
 * @param acceptedProofs the proofs the signer may confirm with
 * @param entries        what the signer typed into fields of their own
 */
public record ParkedSigningStart(
        int stationId,
        int fieldId,
        UUID requestUid,
        String fieldName,
        SignerCapacity capacity,
        int accountId,
        @Nullable Integer memberId,
        byte[] nonce,
        byte[] challenge,
        Set<StepUpProof> acceptedProofs,
        List<SignerEntry> entries) {
    /** Copies the proofs and the entries, so they cannot change after the fact. */
    public ParkedSigningStart {
        acceptedProofs = Set.copyOf(acceptedProofs);
        entries = List.copyOf(entries);
    }

    /**
     * @param stationId  the station the act happens at
     * @param fieldId    the signature field the act fills
     * @param requestUid the request the field belongs to
     * @param fieldName  the field's name in the document
     * @param signer     who signs, and through whose account
     * @param started    what the provider issued
     * @param entries    what the signer typed into fields of their own
     * @return the start as it is kept
     */
    public static ParkedSigningStart of(
            int stationId,
            int fieldId,
            UUID requestUid,
            String fieldName,
            Signer signer,
            SigningStart.InEmber started,
            List<SignerEntry> entries) {
        return new ParkedSigningStart(
                stationId,
                fieldId,
                requestUid,
                fieldName,
                signer.capacity(),
                signer.accountId(),
                signer.memberId(),
                started.nonce(),
                started.challenge(),
                started.acceptedProofs(),
                entries);
    }

    /** @return who signs, and through whose account */
    public Signer signer() {
        return new Signer(capacity, accountId, memberId);
    }
}
