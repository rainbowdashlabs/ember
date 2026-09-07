/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * The two things a member sets going during a self-check that nobody answers.
 */
public enum SelfCheckRaisedKind {
    /**
     * A piece the member cannot find, counted as missing from the moment it is said.
     */
    LOSS,
    /**
     * A different size asked for, on its way rather than waiting.
     */
    EXCHANGE
}
