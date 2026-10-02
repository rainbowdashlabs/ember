/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * One requirement as a station reads it, with the name of what it asks for and where it was written.
 *
 * @param requirement   the row itself
 * @param inventoryName what the requirement points at
 * @param fromCluster   whether the cluster above the station wrote it, in which case the station may
 *                      read it and nothing more
 */
public record VisibleRequirement(InventoryRequirement requirement, String inventoryName, boolean fromCluster) {}
