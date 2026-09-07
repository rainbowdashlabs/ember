/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What a reviewer has made of one answer. Nothing here is a judgement on whether the member told
 * the truth: a refusal is for a row that cannot be settled at all.
 */
public enum SelfCheckRowState {
    /**
     * Nobody has settled it yet.
     */
    OUTSTANDING,
    /**
     * The reviewer accepted it and whatever follows from it has been written.
     */
    TAKEN,
    /**
     * The reviewer sent it back with a reason.
     */
    REFUSED
}
