/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import org.jspecify.annotations.Nullable;

/**
 * Request data for a single item check result.
 *
 * @param itemId      the item ID, or {@code null}
 * @param inventoryId the inventory ID, or {@code null}
 * @param result      the check result
 * @param note        an optional note
 */
public record CheckItemRequest(
        @Nullable Integer itemId, @Nullable Integer inventoryId, CheckResult result, String note) {}
