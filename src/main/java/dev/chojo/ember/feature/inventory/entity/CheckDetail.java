/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.util.List;

/**
 * Detailed view of an inventory check including the checker's name and all checked items.
 *
 * @param check            the inventory check record
 * @param checkerFirstName the checker's first name
 * @param checkerLastName  the checker's last name
 * @param items            the list of checked items
 */
public record CheckDetail(
        InventoryCheck check, String checkerFirstName, String checkerLastName, List<InventoryCheckItem> items) {}
