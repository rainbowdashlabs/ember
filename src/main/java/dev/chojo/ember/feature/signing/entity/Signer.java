/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * Who signs, and through whose account.
 *
 * <p>The account is always the one whose step-up confirms the act. A member is named when the signature
 * concerns somebody else than the account holder: the member a guardian signs for, or the member who
 * signs their own field through the account.
 *
 * @param capacity  in what capacity the signature is given
 * @param accountId the account whose step-up confirms the act
 * @param memberId  the member the guardian signs for or who signs through the account, null exactly
 *                  when the account holder signs for themselves
 */
public record Signer(
        SignerCapacity capacity, int accountId, @Nullable Integer memberId) {
    /** Refuses a member where the account holder signs alone, and a missing one everywhere else. */
    public Signer {
        if ((capacity == SignerCapacity.ACCOUNT_HOLDER) != (memberId == null)) {
            throw new IllegalArgumentException(
                    "A member is named exactly when the account holder does not sign for themselves");
        }
    }

    /**
     * @param accountId the account of the person signing
     * @return the holder of the account signing for themselves
     */
    public static Signer accountHolder(int accountId) {
        return new Signer(SignerCapacity.ACCOUNT_HOLDER, accountId, null);
    }

    /**
     * @param accountId the guardian's account
     * @param memberId  the member in their care
     * @return a guardian signing on behalf of the member
     */
    public static Signer guardian(int accountId, int memberId) {
        return new Signer(SignerCapacity.GUARDIAN, accountId, memberId);
    }

    /**
     * @param accountId the account whose step-up confirms, usually a guardian's
     * @param memberId  the member who signs
     * @return a member signing their own field through the account
     */
    public static Signer memberThroughAccount(int accountId, int memberId) {
        return new Signer(SignerCapacity.MEMBER_THROUGH_ACCOUNT, accountId, memberId);
    }

    /** @return whether the person signing used an account that is not their own */
    public boolean throughAnotherAccount() {
        return capacity == SignerCapacity.MEMBER_THROUGH_ACCOUNT;
    }
}
