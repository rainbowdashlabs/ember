/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.ContainerEventKind;
import dev.chojo.ember.feature.inventory.entity.ContainerPath;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryContainer;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryContainerRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static Inventory inventory;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("container@test.example", "Container", "User");
        station = stationRepo.create("ContainerStation");
        member = stationMemberRepo.create(station.id(), account.id());
        inventory = inventoryRepo.create(station.id(), "Props", InventoryType.INTERNAL, false);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void crudPathAndSubtreeWalk() {
        InventoryContainer hall = containerRepo.create(station.id(), null, "HALL", "Hall A", null, "", member.id());
        InventoryContainer drawer1 = containerRepo.create(station.id(), hall.id(), null, "Drawer 1", null, "", null);
        InventoryContainer drawer2 = containerRepo.create(station.id(), hall.id(), null, "Drawer 2", null, "", null);
        InventoryContainer boxA = containerRepo.create(station.id(), drawer1.id(), "BOX-A", "Box A", null, "", null);
        InventoryContainer boxB = containerRepo.create(station.id(), drawer1.id(), "BOX-B", "Box B", null, "", null);
        InventoryContainer boxC = containerRepo.create(station.id(), drawer2.id(), null, "Box C", null, "", null);

        assertTrue(containerRepo.findById(hall.id()).isPresent());
        assertEquals(
                "Hall A",
                containerRepo
                        .findByInternalId(station.id(), "HALL")
                        .orElseThrow()
                        .name());
        assertTrue(containerRepo.findByInternalId(station.id(), "MISSING-SCAN").isEmpty());

        List<InventoryContainer> stationContainers = containerRepo.findByStation(station.id());
        assertEquals(6, stationContainers.size());
        assertEquals(1, containerRepo.findRoots(station.id()).size());
        assertEquals(2, containerRepo.findChildren(hall.id()).size());

        List<InventoryContainer> walk = containerRepo.findSubtree(hall.id());
        assertEquals(6, walk.size());
        assertEquals(hall.id(), walk.get(0).id());
        assertEquals(drawer1.id(), walk.get(1).id());
        assertEquals(boxA.id(), walk.get(2).id());
        assertEquals(boxB.id(), walk.get(3).id());
        assertEquals(drawer2.id(), walk.get(4).id());
        assertEquals(boxC.id(), walk.get(5).id());

        ContainerPath path = containerRepo.findPath(boxA.id());
        assertEquals(List.of("Hall A", "Drawer 1", "Box A"), path.segments());
        assertEquals(List.of(hall.id(), drawer1.id(), boxA.id()), path.ids());
        assertEquals("Hall A / Drawer 1 / Box A", path.display());

        ContainerPath rootPath = containerRepo.findPath(hall.id());
        assertEquals(List.of("Hall A"), rootPath.segments());
        assertEquals("Hall A", rootPath.display());

        ContainerPath missingPath = containerRepo.findPath(987654321);
        assertTrue(missingPath.segments().isEmpty());
        assertEquals(ContainerPath.empty(), missingPath);

        assertTrue(containerRepo.internalIdExists(station.id(), "HALL", null));
        assertFalse(containerRepo.internalIdExists(station.id(), "HALL", hall.id()));
        assertFalse(containerRepo.internalIdExists(station.id(), "GHOST", null));

        InventoryItem heldItem = inventoryRepo.createItem(
                inventory.id(), "ITEM-A", "Folding Chair", null, InventoryItemMetadata.empty());
        InventoryItem otherItem =
                inventoryRepo.createItem(inventory.id(), "ITEM-B", "Cable", null, InventoryItemMetadata.empty());
        inventoryRepo.setItemContainer(heldItem.id(), boxA.id());
        inventoryRepo.setItemContainer(otherItem.id(), boxC.id());

        List<InventoryItem> directBoxA = containerRepo.findItemsInContainer(boxA.id());
        assertEquals(1, directBoxA.size());
        assertEquals("Folding Chair", directBoxA.getFirst().name());

        List<InventoryItem> subtreeItems = containerRepo.findItemsInSubtree(hall.id());
        assertEquals(2, subtreeItems.size());

        Optional<InventoryItem> reloaded = inventoryRepo.findItemById(heldItem.id());
        assertEquals(Integer.valueOf(boxA.id()), reloaded.orElseThrow().containerId());

        InventoryContainer renamed = containerRepo
                .findById(boxA.id())
                .map(c -> {
                    containerRepo.update(
                            c.id(), c.parentId(), c.internalId(), "Box Alpha", c.kindId(), c.description());
                    return c;
                })
                .orElseThrow();
        assertEquals(
                "Box Alpha", containerRepo.findById(renamed.id()).orElseThrow().name());

        assertEquals(
                List.of(boxA.id()),
                containerRepo.findContainerIdsByInternalIds(station.id(), List.of("BOX-A", "GHOST")));
        assertTrue(containerRepo
                .findContainerIdsByInternalIds(station.id(), List.of())
                .isEmpty());

        containerRepo.appendHistory(
                hall.id(), station.id(), ContainerEventKind.RENAMED, member.id(), "{\"from\":\"x\",\"to\":\"y\"}");
        containerRepo.appendHistory(hall.id(), station.id(), ContainerEventKind.MOVED, null, null);
        assertEquals(2, containerRepo.findHistory(hall.id()).size());
        assertFalse(containerRepo.findRecentHistory(station.id(), 1).isEmpty());

        containerRepo.appendHistory(null, station.id(), ContainerEventKind.DELETED, member.id(), "{\"id\":42}");
        assertTrue(containerRepo.findRecentHistory(station.id(), 10).stream()
                .anyMatch(h -> h.eventKind() == ContainerEventKind.DELETED));

        assertTrue(containerRepo.delete(hall.id()));
        assertFalse(containerRepo.delete(hall.id()));
        assertFalse(containerRepo.update(hall.id(), null, null, "still gone", null, ""));

        List<InventoryContainer> orphans = containerRepo.findRoots(station.id());
        assertTrue(orphans.stream().anyMatch(c -> c.id() == drawer1.id()));
    }
}
