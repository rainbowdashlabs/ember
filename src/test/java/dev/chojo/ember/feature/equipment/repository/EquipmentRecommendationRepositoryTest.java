/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.repository;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentRecommendationRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Station other;
    private static Inventory radios;
    private static Inventory misc;
    private static InventoryItem radio;
    private static InventoryItem sameShelf;
    private static InventoryItem charger;
    private static InventoryItem unrelated;
    private static final EquipmentRecommendationRepository REPOSITORY = new EquipmentRecommendationRepository();

    @BeforeAll
    static void setup() {
        station = stationRepo.create("RecoStation");
        other = stationRepo.create("RecoOther");
        radios = inventoryRepo.create(station.id(), "RecoFunk", InventoryType.INTERNAL, false, false);
        misc = inventoryRepo.create(station.id(), "RecoSonstiges", InventoryType.INTERNAL, false, false);

        radio = item(radios, "RC-01", "Funk");
        sameShelf = item(radios, "RC-02", "Koffer");
        charger = item(misc, "RC-03", "Ladestation");
        unrelated = item(misc, "RC-04", "Playmobil");

        var word = inventoryTagRepo.create(station.id(), "RecoFunkset", null);
        inventoryTagRepo.setItemTags(radio.id(), station.id(), List.of(word.id()));
        inventoryTagRepo.setItemTags(charger.id(), station.id(), List.of(word.id()));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(other.id());
    }

    private static InventoryItem item(Inventory inventory, String internalId, String name) {
        return inventoryRepo.createItem(inventory.id(), internalId, name, null, InventoryItemMetadata.empty());
    }

    @Test
    void aSharedWordComesBeforeTheShelf() {
        var found = REPOSITORY.forItem(station.id(), radio.id(), 10);
        assertEquals(charger.id(), found.getFirst().itemId());
        assertTrue(found.getFirst().byWord());
        assertTrue(found.stream().anyMatch(r -> r.itemId() == sameShelf.id() && !r.byWord()));
        assertTrue(found.stream().noneMatch(r -> r.itemId() == unrelated.id()));
        assertTrue(found.stream().noneMatch(r -> r.itemId() == radio.id()));
    }

    @Test
    void aPieceWithNoWordStillOffersItsShelf() {
        var found = REPOSITORY.forItem(station.id(), unrelated.id(), 10);
        assertTrue(found.stream().anyMatch(r -> r.itemId() == charger.id()));
        assertTrue(found.stream().allMatch(r -> !r.byWord()));
        assertEquals("RecoSonstiges", found.getFirst().inventoryName());
    }

    @Test
    void anotherStationGetsNothing() {
        assertTrue(REPOSITORY.forItem(other.id(), radio.id(), 10).isEmpty());
    }

    @Test
    void theLimitHolds() {
        assertFalse(REPOSITORY.forItem(station.id(), radio.id(), 1).size() > 1);
    }
}
