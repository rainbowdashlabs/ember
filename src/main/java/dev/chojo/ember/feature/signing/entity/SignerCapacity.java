/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * In what capacity somebody signs, which decides whose name the signature carries and whose account
 * confirmed it.
 */
public enum SignerCapacity {
    /** The holder of the account signs for themselves, in their own field. */
    ACCOUNT_HOLDER,
    /** The holder of the account signs as guardian, on behalf of a member in their care. */
    GUARDIAN,
    /**
     * A member signs their own field through somebody else's account: a child without a login signs on
     * the guardian's device, confirmed by the guardian's step-up. The signature is the member's.
     */
    MEMBER_THROUGH_ACCOUNT
}
