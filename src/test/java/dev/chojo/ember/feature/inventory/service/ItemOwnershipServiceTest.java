/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Who owns a single piece of a mixed inventory: changed while it rests in the store, never while it is away,
 * and only where the inventory holds both kinds of gear.
 */
class ItemOwnershipServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private ItemOwnershipService service() {
        return new ItemOwnershipService(inventoryRepo, itemMovementRepo, clusterRepo);
    }

    @Test
    void aPieceInTheStoreChangesHandsWithTheCustodyThatGoesWithIt() {
        var station = stationRepo.create("Wache Eigentum " + NAMES.incrementAndGet());
        var inventory = inventoryRepo.create(station.id(), "Gemischt", InventoryType.MIXED, false);
        int itemId = inventoryRepo
                .createItem(inventory.id(), "E-1", "Helm", null, null, ItemOwner.STATION, null)
                .id();
        try {
            var reowned = service().changeOwner(station.id(), itemId, ItemOwner.CLUSTER);
            assertEquals(ItemOwner.CLUSTER, reowned.ownerKind());
            assertNull(reowned.ownerClusterId(), "a station connected to no association gives it to one outside Ember");
            assertEquals(ItemCustody.AT_STATION, reowned.custody(), "the association's gear is held at the station");

            var back = service().changeOwner(station.id(), itemId, ItemOwner.STATION);
            assertEquals(ItemOwner.STATION, back.ownerKind());
            assertEquals(ItemCustody.WITH_OWNER, back.custody(), "the station's own gear rests in its own store");
        } finally {
            stationRepo.delete(station.id());
        }
    }

    @Test
    void onlyAPieceOfAMixedInventoryThatRestsInTheStoreChangesHands() {
        var station = stationRepo.create("Wache Eigentum " + NAMES.incrementAndGet());
        var internal = inventoryRepo.create(station.id(), "Eigen", InventoryType.INTERNAL, false);
        var mixed = inventoryRepo.create(station.id(), "Gemischt", InventoryType.MIXED, false);
        int own = inventoryRepo
                .createItem(internal.id(), "E-2", "Helm", null, null, ItemOwner.STATION, null)
                .id();
        int worn = inventoryRepo
                .createItem(mixed.id(), "E-3", "Jacke", null, null, ItemOwner.STATION, null)
                .id();
        int member = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("eigentum" + NAMES.incrementAndGet() + "@test.com", "E", "T")
                                .id())
                .id();
        itemCustodyService.assignToMember(worn, member, "E T");
        try {
            assertEquals(
                    InventoryRefusal.INVENTORY_OWNER_SWITCH_ONLY_IN_MIXED,
                    assertThrows(RefusalResponse.class, () -> service()
                                    .changeOwner(station.id(), own, ItemOwner.CLUSTER))
                            .refusal());
            assertEquals(
                    InventoryRefusal.INVENTORY_OWNER_SWITCH_NOT_AT_REST,
                    assertThrows(RefusalResponse.class, () -> service()
                                    .changeOwner(station.id(), worn, ItemOwner.CLUSTER))
                            .refusal());
            assertEquals(
                    InventoryRefusal.ITEM_NOT_HERE,
                    assertThrows(RefusalResponse.class, () -> service().changeOwner(-1, own, ItemOwner.CLUSTER))
                            .refusal(),
                    "another station's piece is not there");
        } finally {
            stationRepo.delete(station.id());
        }
    }
}
