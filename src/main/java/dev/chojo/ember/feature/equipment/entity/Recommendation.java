/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

import org.jspecify.annotations.Nullable;

/**
 * One piece that goes with another.
 *
 * @param byWord whether it was found through a shared word rather than through the shelf it is on
 */
public record Recommendation(
        int itemId,
        String itemName,
        String internalId,
        int inventoryId,
        String inventoryName,
        @Nullable Integer artId,
        String artName,
        boolean byWord) {}
