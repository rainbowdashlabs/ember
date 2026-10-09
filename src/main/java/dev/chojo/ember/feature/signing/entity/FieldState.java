/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/** Where one signature field of a request stands, which is the requirement status of its signer. */
public enum FieldState {
    /** It waits for a signature. */
    OPEN,
    /** A signing act filled it; its evidence is stored. */
    SIGNED,
    /** A manager confirmed that it was signed on paper. */
    PAPER_CONFIRMED,
    /** A manager let it go. */
    WAIVED,
    /** The signature is no longer asked for. */
    WITHDRAWN
}
