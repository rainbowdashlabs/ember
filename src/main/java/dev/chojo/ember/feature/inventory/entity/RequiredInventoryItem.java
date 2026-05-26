/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.util.List;

/**
 * Describes an inventory requirement for a member, with comparison of required vs assigned quantities.
 *
 * @param inventoryId      the inventory ID
 * @param inventoryName    the inventory name
 * @param inventoryType    the inventory type
 * @param hasSizes         whether the inventory supports sizes
 * @param sizes            the available sizes if applicable
 * @param requiredQuantity the total required quantity
 * @param assignedQuantity the currently assigned quantity
 */
public record RequiredInventoryItem(
        int inventoryId,
        String inventoryName,
        String inventoryType,
        boolean hasSizes,
        List<InventorySize> sizes,
        int requiredQuantity,
        int assignedQuantity) {}
