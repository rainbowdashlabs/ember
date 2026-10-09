/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.ArtChoice;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.InventoryChoice;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.ItemChoice;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentChoiceServiceTest extends RepositoryTestBase {

    private static Station station;
    private static Station other;
    private static Inventory drawer;
    private static Inventory shelf;
    private static EquipmentChoiceService choices;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("ChoiceSvcStation");
        other = stationRepo.create("ChoiceSvcOther");
        drawer = inventoryRepo.create(station.id(), "ChoiceSvcFunk", InventoryType.INTERNAL, false, false);
        shelf = inventoryRepo.create(station.id(), "ChoiceSvcJacken", InventoryType.INTERNAL, true, true);
        var blue = artRepo.create(drawer.id(), "ChoiceSvcBlau", "", 0);
        inventoryRepo.createItem(
                drawer.id(), "CS-01", "Funk blau", null, blue.id(), InventoryItemMetadata.empty(), null, null);
        inventoryRepo.createSize(shelf.id(), "XL", 0, null);
        int xl = inventoryService.findAllSizesByStation(station.id()).getFirst().id();
        inventoryRepo.createItem(shelf.id(), "CS-02", "Jacke", xl, InventoryItemMetadata.empty());
        var foreign = inventoryRepo.create(other.id(), "ChoiceSvcFremd", InventoryType.INTERNAL, false, false);
        inventoryRepo.createItem(foreign.id(), "CS-99", "Fremd", null, InventoryItemMetadata.empty());
        choices = new EquipmentChoiceService(inventoryService, artService);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(other.id());
    }

    @Test
    void offersTheStationsOwnInventoriesKindsAndPieces() {
        var offered = choices.choicesFor(station.id());

        assertEquals(
                List.of("ChoiceSvcFunk", "ChoiceSvcJacken"),
                offered.inventories().stream()
                        .map(InventoryChoice::name)
                        .sorted()
                        .toList());
        assertEquals(
                List.of("ChoiceSvcBlau"),
                offered.arts().stream().map(ArtChoice::name).toList(),
                "only an inventory of different things has kinds");
        assertEquals(
                List.of("CS-01", "CS-02"),
                offered.items().stream().map(ItemChoice::internalId).sorted().toList());
    }

    @Test
    void aPieceCarriesTheLabelOfItsSize() {
        var jacket = choices.choicesFor(station.id()).items().stream()
                .filter(item -> item.inventoryId() == shelf.id())
                .findFirst()
                .orElseThrow();

        assertEquals("XL", jacket.sizeLabel());
    }
}
