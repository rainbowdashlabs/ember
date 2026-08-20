/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Names the party that owns an inventory item. There is never more than one body above a station,
 * and members never own tracked items: gear a member bought is the member's business and is not
 * recorded here at all.
 */
public enum ItemOwner {
    /**
     * The station that runs the item's inventory owns it.
     */
    STATION,
    /**
     * The one body above that station owns it: the municipality, the district association or the
     * umbrella organisation. Whether that body runs on this instance is told by the item's owning
     * cluster, which is set when it does and null when it does not.
     */
    CLUSTER
}
