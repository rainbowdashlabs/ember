/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Defines the ownership model for items in an inventory.
 */
public enum InventoryType {
    /** All items are owned by the organization. */
    INTERNAL,
    /** All items are owned by individual members. */
    EXTERNAL,
    /** Items may be owned by the organization or individual members. */
    MIXED
}
