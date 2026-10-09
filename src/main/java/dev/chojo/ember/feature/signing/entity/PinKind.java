/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/** How a partner's signing authority came to be pinned. */
public enum PinKind {
    /** With the first statement accepted from the partner, when nothing was pinned for it yet. */
    FIRST_FETCH,
    /** With a later statement, after the partner's installation renewed or re-issued its authority. */
    ANNOUNCED
}
