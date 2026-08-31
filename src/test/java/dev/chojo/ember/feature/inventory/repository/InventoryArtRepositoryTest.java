/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statements behind the kinds: the list, the counts, the merge key the database maintains, and
 * the two tidying writes.
 */
class InventoryArtRepositoryTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("ArtRepoStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static Inventory drawer() {
        return inventoryRepo.create(
                station.id(), "ArtRepo " + NAMES.incrementAndGet(), InventoryType.INTERNAL, false, false);
    }

    private static InventoryItem piece(int inventoryId, String name, Integer artId) {
        return inventoryRepo.createItem(inventoryId, null, name, null, artId, null, ItemOwner.STATION, null);
    }

    @Test
    void theMergeKeyIsMaintainedByTheDatabase() {
        Inventory drawer = drawer();
        InventoryArt art = artRepo.create(drawer.id(), "  Funkgerät BLAU  ", "eine Notiz", 3);
        assertEquals("funkgerät blau", art.mergeKey());
        assertEquals("eine Notiz", art.note());
        assertEquals(3, art.position());

        assertTrue(artRepo.update(art.id(), "Funkgerät grün", "", 4));
        assertEquals("funkgerät grün", artRepo.findById(art.id()).orElseThrow().mergeKey());
        assertTrue(artRepo.findByName(drawer.id(), " FUNKGERÄT GRÜN ").isPresent());
        assertFalse(artRepo.findByMergeKey("funkgerät grün").isEmpty());
    }

    @Test
    void countsLeaveThePiecesWithNoKindOut() {
        Inventory drawer = drawer();
        InventoryArt blau = artRepo.create(drawer.id(), "blau", "", 0);
        piece(drawer.id(), "blau", blau.id());
        piece(drawer.id(), "blau", blau.id());
        piece(drawer.id(), "Ladestation", null);

        var stock = artRepo.stockByInventory(drawer.id());
        assertEquals(1, stock.size());
        assertEquals(2, stock.getFirst().pieces());
        assertEquals(2, stock.getFirst().free());
        assertEquals(2, artRepo.freeOfArt(blau.id()));

        var names = artRepo.nameCounts(drawer.id());
        assertEquals(2, names.size());
        assertEquals("blau", names.getFirst().name());
        assertEquals(2, names.getFirst().pieces());
        assertEquals(0, names.getFirst().unassigned());
    }

    @Test
    void oneTidyingWriteRenamesAndTheOtherDoesNot() {
        Inventory drawer = drawer();
        InventoryArt orange = artRepo.create(drawer.id(), "Funkgerät orange", "", 0);
        InventoryItem typo = piece(drawer.id(), "Funkgerät organge", null);
        InventoryItem pager = piece(drawer.id(), "Pager 01", null);

        assertEquals(1, artRepo.mergeIntoArt(orange.id(), List.of(typo.id()), orange.name()));
        assertEquals(
                "Funkgerät orange",
                inventoryRepo.findItemById(typo.id()).orElseThrow().name());

        assertEquals(1, artRepo.setArt(orange.id(), List.of(pager.id())));
        assertEquals(
                "Pager 01", inventoryRepo.findItemById(pager.id()).orElseThrow().name());

        assertEquals(0, artRepo.setArt(orange.id(), List.of()));
        assertEquals(0, artRepo.mergeIntoArt(orange.id(), List.of(), orange.name()));

        assertEquals(2, artRepo.setArt(null, List.of(typo.id(), pager.id())));
        assertNull(inventoryRepo.findItemById(typo.id()).orElseThrow().artId());
    }

    @Test
    void removingAKindDoesNotRemoveItsPieces() {
        Inventory drawer = drawer();
        InventoryArt spiele = artRepo.create(drawer.id(), "Spiele", "", 0);
        InventoryItem game = piece(drawer.id(), "Uno", spiele.id());
        assertEquals(1, artRepo.findByInventory(drawer.id()).size());
        assertEquals(1, inventoryRepo.findItemsOfArt(spiele.id()).size());

        assertTrue(artRepo.delete(spiele.id()));
        assertTrue(artRepo.findByInventory(drawer.id()).isEmpty());
        assertNull(inventoryRepo.findItemById(game.id()).orElseThrow().artId());
    }
}
