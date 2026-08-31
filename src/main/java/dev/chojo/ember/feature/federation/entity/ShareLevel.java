/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

/**
 * What a sharing row speaks about. Listed widest first, and the narrowest row that exists is the one
 * that decides: a piece beats its kind, and a kind beats the inventory holding it.
 */
public enum ShareLevel {
    /** The whole inventory, and with it everything in it that says nothing else. */
    INVENTORY,
    /** One kind of thing inside an inventory. */
    ART,
    /** One single piece. */
    ITEM
}
