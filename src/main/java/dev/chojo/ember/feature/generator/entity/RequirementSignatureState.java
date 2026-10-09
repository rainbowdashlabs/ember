/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Where the signatures a participant's copy of a document asks for stand, as a whole or for one field.
 */
public enum RequirementSignatureState {
    /** A signature is still missing. */
    OPEN,
    /** Signed online. */
    SIGNED,
    /** Confirmed by a manager as signed on paper. */
    PAPER_CONFIRMED,
    /** Let go without a signature, or no longer asked for. */
    WAIVED,
    /** Signed, and then withdrawn by a signer; the copy as a whole only, its fields keep their state. */
    REVOKED
}
