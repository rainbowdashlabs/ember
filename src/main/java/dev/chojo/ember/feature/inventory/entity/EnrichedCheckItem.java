/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * A check item enriched with resolved names for display.
 *
 * @param id            the check item ID
 * @param itemId        the item ID, or {@code null}
 * @param itemName      the resolved item name
 * @param internalId    the item's internal ID
 * @param inventoryName the inventory name
 * @param sizeName      the size label, or {@code null}
 * @param result        the check result
 * @param note          the note
 */
public record EnrichedCheckItem(
        int id,
        Integer itemId,
        String itemName,
        String internalId,
        String inventoryName,
        String sizeName,
        CheckResult result,
        String note) {}
