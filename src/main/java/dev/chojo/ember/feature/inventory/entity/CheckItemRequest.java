/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Request data for a single item check result.
 *
 * @param itemId      the item ID, or {@code null}
 * @param inventoryId the inventory ID, or {@code null}
 * @param result      the check result
 * @param note        an optional note
 */
public record CheckItemRequest(Integer itemId, Integer inventoryId, CheckResult result, String note) {}
