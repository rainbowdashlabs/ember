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
 * @param check             the inventory check record
 * @param checkerFirstName  the first name of whoever signed the check off
 * @param checkerLastName   the last name of whoever signed the check off
 * @param reporterFirstName the first name of whoever said what was there, empty on a check somebody
 *                          walked themselves
 * @param reporterLastName  the last name of whoever said what was there, empty on a check somebody
 *                          walked themselves
 * @param items             the enriched check items
 */
public record EnrichedCheckDetail(
        InventoryCheck check,
        String checkerFirstName,
        String checkerLastName,
        String reporterFirstName,
        String reporterLastName,
        List<EnrichedCheckItem> items) {}
