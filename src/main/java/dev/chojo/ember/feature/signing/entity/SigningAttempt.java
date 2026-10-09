/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

/**
 * A signing act that has started and waits for the signer's confirmation, with what the signer is shown
 * before they confirm.
 *
 * @param startToken          the token the start is kept under, handed back with the confirmation
 * @param expiresAt           when the start can no longer be completed
 * @param field               the field to sign and the document it is on
 * @param signer              who signs, and through whose account
 * @param accountHolderName   the official name of the holder of the confirming account
 * @param memberName          the official name of the member the act concerns, or null where the account
 *                            holder signs for themselves
 * @param contentSha256       SHA-256 of the document the act binds to, lower-case hexadecimal
 * @param acceptedProofs      the proofs the signer may confirm with
 * @param webAuthnOptionsJson what the browser asks a passkey or security key with, or null where neither is
 *                            among the accepted proofs
 */
public record SigningAttempt(
        String startToken,
        Instant expiresAt,
        PendingSignature field,
        Signer signer,
        String accountHolderName,
        @Nullable String memberName,
        String contentSha256,
        Set<StepUpProof> acceptedProofs,
        @Nullable String webAuthnOptionsJson) {
    /** Copies the proofs, so they cannot change after the fact. */
    public SigningAttempt {
        acceptedProofs = Set.copyOf(acceptedProofs);
    }

    /**
     * @return the official name of whoever signs: the member who signs through the account, else the
     *         account holder
     */
    public String signerName() {
        return signer.throughAnotherAccount() && memberName != null ? memberName : accountHolderName;
    }
}
