/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerKind;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryContainerKindRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("kind@test.example", "Kind", "User");
        station = stationRepo.create("KindStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void createListFindUpdateAndDelete() {
        assertFalse(containerKindRepo.stationHasAnyKind(station.id()));
        InventoryContainerKind room = containerKindRepo.create(station.id(), "room", "Room", "house", 10, true);
        assertTrue(containerKindRepo.stationHasAnyKind(station.id()));
        assertTrue(containerKindRepo.findById(room.id()).isPresent());
        assertEquals(
                "Room",
                containerKindRepo.findByKey(station.id(), "room").orElseThrow().label());
        assertTrue(containerKindRepo.findByKey(station.id(), "missing").isEmpty());

        InventoryContainerKind drawer = containerKindRepo.create(station.id(), "drawer", "Drawer", "inbox", 40, true);
        var all = containerKindRepo.findByStation(station.id());
        assertEquals(2, all.size());
        assertEquals("room", all.getFirst().key());

        assertTrue(containerKindRepo.update(drawer.id(), "Big Drawer", "box-archive", 50, false));
        InventoryContainerKind reloaded =
                containerKindRepo.findById(drawer.id()).orElseThrow();
        assertEquals("Big Drawer", reloaded.label());
        assertEquals("box-archive", reloaded.icon());
        assertFalse(reloaded.enabled());

        assertTrue(containerKindRepo.delete(drawer.id()));
        assertFalse(containerKindRepo.delete(drawer.id()));
        assertFalse(containerKindRepo.update(drawer.id(), "x", "y", 0, true));
        assertTrue(containerKindRepo.findById(drawer.id()).isEmpty());
    }
}
