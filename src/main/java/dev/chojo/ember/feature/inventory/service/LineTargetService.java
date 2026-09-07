/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Whose gear a line points at.
 *
 * <p>Every list built out of the three targets asks the same question before it writes a line: does
 * this belong to the station writing it. A line naming another station's gear would resolve to
 * nothing for ever and say nothing about why, so it is refused where it is written rather than left
 * to be discovered.
 */
@Singleton
public class LineTargetService {

    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;

    @Inject
    public LineTargetService(InventoryRepository inventoryRepository, InventoryArtRepository artRepository) {
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
    }

    /**
     * The station a target belongs to.
     *
     * @param target the target
     * @return the station ID
     * @throws IllegalArgumentException if the target points at something that does not exist
     */
    public int stationOf(LineTarget target) {
        if (target.itemId() != null) {
            InventoryItem item = inventoryRepository
                    .findItemById(target.itemId())
                    .orElseThrow(() -> new IllegalArgumentException("The item does not exist"));
            return stationOfInventory(item.inventoryId());
        }
        if (target.artId() != null) {
            InventoryArt art = artRepository
                    .findById(target.artId())
                    .orElseThrow(() -> new IllegalArgumentException("The kind does not exist"));
            return stationOfInventory(art.inventoryId());
        }
        return stationOfInventory(target.inventoryId());
    }

    /**
     * Refuses a target belonging to another station.
     *
     * @param target    the target
     * @param stationId the station the line is being written for
     * @param refusal   what to say when it belongs to somebody else
     * @throws IllegalArgumentException if the target belongs to another station or does not exist
     */
    public void requireOwnedBy(LineTarget target, int stationId, String refusal) {
        if (stationOf(target) != stationId) throw new IllegalArgumentException(refusal);
    }

    private int stationOfInventory(int inventoryId) {
        Inventory inventory = inventoryRepository
                .findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("The inventory does not exist"));
        return inventory.stationId();
    }
}
