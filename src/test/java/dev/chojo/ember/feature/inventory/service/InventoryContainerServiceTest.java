/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.ContainerPath;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryContainer;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerKind;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryContainerServiceTest extends RepositoryTestBase {

    private static Account account;
    private static InventoryContainerService service;

    @BeforeAll
    static void setup() {
        service = new InventoryContainerService(containerRepo, containerKindRepo, inventoryRepo);
    }

    @org.junit.jupiter.api.BeforeEach
    void ensureAccount() {
        if (account == null || accountRepo.findById(account.id()).isEmpty()) {
            account = accountRepo.create("svc@test.example", "Svc", "User");
        }
    }

    @AfterAll
    static void cleanup() {
        if (account != null && accountRepo.findById(account.id()).isPresent()) {
            accountRepo.delete(account.id());
        }
    }

    @Test
    void seedingAndKindCrud() {
        Station station = stationRepo.create("KindSeedingStation");
        try {
            assertFalse(containerKindRepo.stationHasAnyKind(station.id()));
            List<InventoryContainerKind> kinds = service.listKinds(station.id());
            assertEquals(InventoryContainerService.DEFAULT_KINDS.size(), kinds.size());
            assertEquals(kinds.size(), service.seedDefaultKinds(station.id()).size());
            assertEquals(kinds.size(), service.listKinds(station.id()).size());

            InventoryContainerKind custom = service.createKind(station.id(), "tour-case", "Tour Case", "", 80, true);
            assertEquals("box", custom.icon());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.createKind(station.id(), "tour-case", "Dup", "case", 10, true));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.createKind(station.id(), "", "Empty key", "", 0, true));
            assertThrows(
                    IllegalArgumentException.class, () -> service.createKind(station.id(), "x", "  ", "", 0, true));

            InventoryContainerKind updated = service.updateKind(custom.id(), "Touring Case", "", 90, false)
                    .orElseThrow();
            assertEquals("Touring Case", updated.label());
            assertEquals("box", updated.icon());
            assertThrows(IllegalArgumentException.class, () -> service.updateKind(custom.id(), "", "case", 1, true));
            assertTrue(service.updateKind(99999, "Ghost", "case", 0, true).isEmpty());
            assertTrue(service.deleteKind(custom.id()));
            assertFalse(service.deleteKind(custom.id()));
        } finally {
            stationRepo.delete(station.id());
        }
    }

    @Test
    void containerLifecycleWithHistory() {
        Station station = stationRepo.create("ContainerLifecycleStation");
        StationMember member = stationMemberRepo.create(station.id(), account.id());
        Inventory inventory = inventoryRepo.create(station.id(), "Stage Props", InventoryType.INTERNAL, false);
        Station otherStation = stationRepo.create("OtherLifecycleStation");
        try {
            InventoryContainer hall =
                    service.create(station.id(), null, "SVC-HALL", "Hall One", null, "Main hall", member.id());
            assertNotNull(hall);
            assertEquals("Hall One", hall.name());

            InventoryContainer drawer = service.create(station.id(), hall.id(), null, "Drawer", null, "", member.id());
            InventoryContainer box = service.create(station.id(), drawer.id(), "BOX-1", "Box 1", null, "", null);

            assertTrue(service.findById(hall.id()).isPresent());
            assertEquals(3, service.findByStation(station.id()).size());
            assertEquals(1, service.findRoots(station.id()).size());
            assertEquals(1, service.findChildren(hall.id()).size());
            List<InventoryContainer> walk = service.findSubtree(hall.id());
            assertEquals(3, walk.size());

            ContainerPath path = service.pathOf(box.id());
            assertEquals(List.of("Hall One", "Drawer", "Box 1"), path.segments());

            assertEquals(
                    box.id(),
                    service.resolveScan(station.id(), "BOX-1").orElseThrow().id());
            assertTrue(service.resolveScan(station.id(), "").isEmpty());
            assertTrue(service.resolveScan(station.id(), null).isEmpty());

            InventoryContainer renamed = service.update(
                            box.id(), drawer.id(), "BOX-1", "Box 1 Renamed", null, "now labelled", member.id())
                    .orElseThrow();
            assertEquals("Box 1 Renamed", renamed.name());

            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.update(hall.id(), box.id(), null, "Hall One", null, "", member.id()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.update(hall.id(), null, null, "", null, "", member.id()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.create(station.id(), null, null, "Bad/Name", null, "", member.id()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.create(station.id(), null, "SVC-HALL", "Dupe", null, "", member.id()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.create(station.id(), 987654321, null, "Bad parent", null, "", member.id()));

            InventoryContainer otherStationHall =
                    service.create(otherStation.id(), null, null, "Foreign Hall", null, "", null);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.create(station.id(), otherStationHall.id(), null, "Cross", null, "", member.id()));

            assertEquals(2, service.findHistory(box.id()).size());
            assertFalse(service.findRecentHistory(station.id(), 5).isEmpty());

            InventoryItem item = inventoryRepo.createItem(
                    inventory.id(), "ITEM-LOC", "Located", null, InventoryItemMetadata.empty());
            assertTrue(service.setItemContainer(item.id(), box.id()));
            assertEquals(
                    List.of("Hall One", "Drawer", "Box 1 Renamed"),
                    service.pathOfItem(inventoryRepo.findItemById(item.id()).orElseThrow())
                            .segments());
            assertEquals(0, service.pathOfItem(null).segments().size());

            assertEquals(1, service.findItemsInContainer(box.id()).size());
            assertEquals(1, service.findItemsInSubtree(hall.id()).size());

            assertTrue(service.setItemContainer(item.id(), null));

            assertThrows(
                    IllegalArgumentException.class, () -> service.setItemContainer(item.id(), otherStationHall.id()));
            assertFalse(service.setItemContainer(item.id(), 987654321));

            assertTrue(service.delete(box.id(), member.id()));
            assertFalse(service.delete(box.id(), member.id()));
        } finally {
            stationRepo.delete(station.id());
            stationRepo.delete(otherStation.id());
        }
    }

    @Test
    void containerAndAssignmentAreMutuallyExclusive() {
        Station station = stationRepo.create("ExclusivityStation");
        StationMember member = stationMemberRepo.create(station.id(), account.id());
        Inventory inventory = inventoryRepo.create(station.id(), "Exclusive", InventoryType.INTERNAL, false);
        try {
            InventoryContainer box = service.create(station.id(), null, null, "Exclusive Box", null, "", null);
            InventoryItem item = inventoryRepo.createItem(inventory.id(), null, "Excl Item", null, null);

            inventoryRepo.assignItem(item.id(), member.id());
            assertEquals(
                    Integer.valueOf(member.id()),
                    inventoryRepo.findItemById(item.id()).orElseThrow().assignedTo());
            assertEquals(
                    null, inventoryRepo.findItemById(item.id()).orElseThrow().containerId());

            assertTrue(service.setItemContainer(item.id(), box.id()));
            InventoryItem placed = inventoryRepo.findItemById(item.id()).orElseThrow();
            assertEquals(null, placed.assignedTo());
            assertEquals(Integer.valueOf(box.id()), placed.containerId());

            inventoryRepo.assignItem(item.id(), member.id());
            InventoryItem assigned = inventoryRepo.findItemById(item.id()).orElseThrow();
            assertEquals(Integer.valueOf(member.id()), assigned.assignedTo());
            assertEquals(null, assigned.containerId());

            inventoryRepo.deleteItem(item.id());
            service.delete(box.id(), member.id());
        } finally {
            stationRepo.delete(station.id());
        }
    }

    @Test
    void wouldCreateCycleReturnsTrueForSelfAndDescendants() {
        Station station = stationRepo.create("CycleStation");
        try {
            InventoryContainer root = service.create(station.id(), null, null, "CycleRoot", null, "", null);
            InventoryContainer child = service.create(station.id(), root.id(), null, "CycleChild", null, "", null);
            assertTrue(service.wouldCreateCycle(root.id(), root.id()));
            assertTrue(service.wouldCreateCycle(root.id(), child.id()));
            assertFalse(service.wouldCreateCycle(child.id(), root.id()));
        } finally {
            stationRepo.delete(station.id());
        }
    }
}
