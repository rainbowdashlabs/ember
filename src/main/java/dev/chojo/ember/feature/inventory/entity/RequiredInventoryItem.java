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
 * @param homogeneous      whether the inventory holds one thing in many copies, which is what makes
 *                         a piece of it exchangeable: among a drawer of different things there is
 *                         nothing to swap one for, and the exchange is refused there
 * @param sizes            the available sizes if applicable
 * @param requiredQuantity the total required quantity
 * @param assignedQuantity what the member has towards it, counting what is away in an exchange
 * @param inExchangeQuantity how many of those are away in an exchange rather than in their hands
 */
public record RequiredInventoryItem(
        int inventoryId,
        String inventoryName,
        InventoryType inventoryType,
        boolean hasSizes,
        boolean homogeneous,
        List<InventorySize> sizes,
        int requiredQuantity,
        int assignedQuantity,
        int inExchangeQuantity) {}
