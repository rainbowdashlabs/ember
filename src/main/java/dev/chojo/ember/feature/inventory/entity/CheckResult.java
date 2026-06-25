/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Represents the possible outcomes when checking whether a member possesses an inventory item.
 */
public enum CheckResult {
    /**
     * The member confirmed possession of the item.
     */
    CONFIRMED,
    /**
     * The member does not currently have the item in their possession.
     */
    NOT_IN_POSSESSION,
    /**
     * The item has been lost.
     */
    LOST,
    /**
     * An item was found that the check did not expect. Used by container-scope
     * checks when the operator scans something that the system does not believe
     * belongs in the container.
     */
    EXTRA
}
