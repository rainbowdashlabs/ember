/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who owns a single piece of a mixed inventory, changed after it was written down.
 *
 * <p>Internal and external inventories need none of this: their type says whose gear they hold, and switching
 * the type rewrites every piece. A mixed inventory holds both, so a piece written down under the wrong owner can
 * only be put right one piece at a time.
 *
 * <p>Only a piece resting in the station's store changes hands. One that is with a member, on its way, lent out
 * or missing has a chain or a record running for the owner it has, and changing the owner underneath it would
 * leave that walking for the wrong one. The association is the one the station answers to in Ember, or one
 * outside Ember where the station is connected to none.
 *
 * <p>A piece of an association that uses Ember is that association's to describe, so the station does not take
 * it back for itself; one of an association outside Ember the station keeps the record of, and may.
 */
@Singleton
public class ItemOwnershipService {
    private static final Logger log = LoggerFactory.getLogger(ItemOwnershipService.class);

    private final InventoryRepository inventoryRepository;
    private final ItemMovementRepository movementRepository;
    private final ClusterRepository clusterRepository;

    @Inject
    public ItemOwnershipService(
            InventoryRepository inventoryRepository,
            ItemMovementRepository movementRepository,
            ClusterRepository clusterRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.clusterRepository = clusterRepository;
    }

    /**
     * Gives a piece another owner.
     *
     * @param stationId the station whose inventory holds the piece
     * @param itemId    the piece
     * @param owner     the station or the association
     * @return the piece as it now stands
     * @throws RefusalResponse when the piece is not the station's, the inventory is not mixed, the piece is
     *                         borrowed, belongs to an association that uses Ember, is not at rest in the
     *                         store, or a movement is running on it
     */
    public InventoryItem changeOwner(int stationId, int itemId, ItemOwner owner) {
        InventoryItem item =
                inventoryRepository.findItemById(itemId).orElseThrow(InventoryRefusal.ITEM_NOT_HERE::raise);
        Inventory inventory = inventoryRepository
                .findById(item.inventoryId())
                .filter(found -> found.stationId() == stationId)
                .orElseThrow(InventoryRefusal.ITEM_NOT_HERE::raise);
        if (item.ownerKind() == ItemOwner.PARTNER_STATION || owner == ItemOwner.PARTNER_STATION) {
            throw InventoryRefusal.INVENTORY_OWNER_SWITCH_BORROWED.raise();
        }
        if (inventory.inventoryType() != InventoryType.MIXED) {
            throw InventoryRefusal.INVENTORY_OWNER_SWITCH_ONLY_IN_MIXED.raise();
        }
        if (item.ownerKind() == owner) return item;
        if (item.ownerKind() == ItemOwner.CLUSTER && item.ownerClusterId() != null) {
            throw InventoryRefusal.INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DESCRIBE.raise();
        }
        if (!restsInTheStore(item)) throw InventoryRefusal.INVENTORY_OWNER_SWITCH_NOT_AT_REST.raise();
        if (movementRepository.findOpenByOutgoingItem(itemId).isPresent()
                || movementRepository.findOpenByIncomingItem(itemId).isPresent()) {
            throw InventoryRefusal.INVENTORY_OWNER_SWITCH_ON_A_MOVEMENT.raise();
        }
        Integer association = owner == ItemOwner.CLUSTER
                ? clusterRepository.findByStation(stationId).map(Cluster::id).orElse(null)
                : null;
        inventoryRepository.reown(itemId, owner, association, stationId);
        log.info("Item {} of station {} now belongs to {} (association={})", itemId, stationId, owner, association);
        return inventoryRepository.findItemById(itemId).orElse(item);
    }

    /** Whether the piece lies in the station's store: its own gear with itself, the association's at it. */
    private static boolean restsInTheStore(InventoryItem item) {
        return switch (item.ownerKind()) {
            case STATION -> item.custody() == ItemCustody.WITH_OWNER;
            case CLUSTER -> item.custody() == ItemCustody.AT_STATION;
            case PARTNER_STATION -> false;
        };
    }
}
