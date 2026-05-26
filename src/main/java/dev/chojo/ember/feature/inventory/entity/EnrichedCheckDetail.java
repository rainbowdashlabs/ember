/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.util.List;

/**
 * Enriched inventory check detail with resolved item names and sizes.
 *
 * @param check            the inventory check record
 * @param checkerFirstName the checker's first name
 * @param checkerLastName  the checker's last name
 * @param items            the enriched check items
 */
public record EnrichedCheckDetail(
        InventoryCheck check, String checkerFirstName, String checkerLastName, List<EnrichedCheckItem> items) {}
