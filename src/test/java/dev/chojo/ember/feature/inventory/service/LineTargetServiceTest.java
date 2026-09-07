/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineTargetServiceTest extends RepositoryTestBase {

    private static Station station;
    private static Station other;
    private static Inventory drawer;
    private static InventoryArt blue;
    private static InventoryItem radio;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("LineTargetStation");
        other = stationRepo.create("LineTargetOther");
        drawer = inventoryRepo.create(station.id(), "LineTargetFunk", InventoryType.INTERNAL, false, false);
        blue = artRepo.create(drawer.id(), "LineTargetBlau", "", 0);
        radio = inventoryRepo.createItem(
                drawer.id(), "LT-01", "Funk", null, blue.id(), InventoryItemMetadata.empty(), null, null);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(other.id());
    }

    @Test
    void everyLevelResolvesToItsStation() {
        assertEquals(station.id(), lineTargetService.stationOf(LineTarget.item(radio.id())));
        assertEquals(station.id(), lineTargetService.stationOf(LineTarget.art(blue.id())));
        assertEquals(station.id(), lineTargetService.stationOf(LineTarget.inventory(drawer.id())));
    }

    @Test
    void gearOfAnotherStationIsRefused() {
        lineTargetService.requireOwnedBy(LineTarget.item(radio.id()), station.id(), "nope");
        var refusal = assertThrows(
                IllegalArgumentException.class,
                () -> lineTargetService.requireOwnedBy(LineTarget.item(radio.id()), other.id(), "not yours"));
        assertEquals("not yours", refusal.getMessage());
    }

    @Test
    void aTargetThatIsGoneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> lineTargetService.stationOf(LineTarget.item(-1)));
        assertThrows(IllegalArgumentException.class, () -> lineTargetService.stationOf(LineTarget.art(-1)));
        assertThrows(IllegalArgumentException.class, () -> lineTargetService.stationOf(LineTarget.inventory(-1)));
    }

    @Test
    void aLineNamesExactlyOneThing() {
        assertThrows(IllegalArgumentException.class, () -> new LineTarget(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LineTarget(1, 2, null));
        assertTrue(LineTarget.item(1).namesItem());
        assertTrue(!LineTarget.art(1).namesItem());
        assertEquals(LineTarget.art(7), LineTarget.of(null, 7, null));
    }
}
