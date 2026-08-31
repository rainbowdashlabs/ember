/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryServiceTest extends RepositoryTestBase {
    private static InventoryService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static int itemId;

    @BeforeAll
    static void setup() {
        service = new InventoryService(inventoryRepo, itemCustodyService, clusterRepo, clusterStationGroupRepo);
        station = stationRepo.create("InvSvcStation");
        account = accountRepo.create("inv-svc@test.com", "Inv", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createInventory() {
        var inv = service.create(station.id(), "Helmets", InventoryType.INTERNAL, true, true);
        assertNotNull(inv);
        assertEquals("Helmets", inv.name());
        assertTrue(inv.hasSizes());
        inventoryId = inv.id();
    }

    @Test
    @Order(2)
    void findByStation() {
        var list = service.findByStation(station.id());
        assertTrue(list.stream().anyMatch(i -> i.id() == inventoryId));
    }

    @Test
    @Order(3)
    void findById() {
        assertTrue(service.findById(inventoryId).isPresent());
    }

    @Test
    @Order(10)
    void createSize() {
        var sizes = service.createSize(inventoryId, "M", 0, null);
        assertNotNull(sizes);
        assertFalse(sizes.isEmpty());
        assertTrue(sizes.stream().anyMatch(s -> "M".equals(s.label())));
    }

    @Test
    @Order(11)
    void findSizes() {
        var sizes = service.findSizes(inventoryId);
        assertFalse(sizes.isEmpty());
    }

    @Test
    @Order(20)
    void createItem() {
        var sizes = service.findSizes(inventoryId);
        var sizeId = sizes.isEmpty() ? null : sizes.getFirst().id();
        var item = service.createItem(inventoryId, "H-001", "Helmet 1", sizeId, null);
        assertNotNull(item);
        itemId = item.id();
    }

    @Test
    @Order(21)
    void findItems() {
        var items = service.findItems(inventoryId);
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(30)
    void assignItem() {
        var result = service.assignItem(itemId, member.id(), "Inv Tester");
        assertTrue(result.isPresent());
        var items = service.findItemsByMember(member.id());
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(31)
    void unassignItem() {
        var result = service.assignItem(itemId, null, "");
        assertTrue(result.isPresent());
        var items = service.findItemsByMember(member.id());
        assertFalse(items.stream().anyMatch(i -> i.id() == itemId));
    }

    /**
     * Handing a member something they are short of, from the member's own page.
     *
     * <p>Two steps that have to be one: a piece written down for somebody and then left lying
     * because the hand-over failed is worse than no piece at all.
     */
    @Test
    @Order(35)
    void aFreshPieceIsWrittenDownAndHandedOverInOneStep() {
        var inv = service.create(station.id(), "HandOut Inv", InventoryType.INTERNAL, false, true);

        var handed = service.createAndHandOut(inv.id(), null, member.id(), "Inv Tester");

        assertEquals(member.id(), handed.assignedTo(), "it is in the member's hands at once");
        assertTrue(service.findItemsByMember(member.id()).stream().anyMatch(i -> i.id() == handed.id()));
        assertTrue(
                service.unassignedItems(inv.id()).stream().noneMatch(i -> i.id() == handed.id()),
                "and no longer offered as free stock");

        service.assignItem(handed.id(), null, "");
        assertTrue(
                service.unassignedItems(inv.id()).stream().anyMatch(i -> i.id() == handed.id()),
                "taking it back puts it on offer again");

        service.deleteItem(handed.id(), null);
        service.delete(inv.id());
    }

    @Test
    @Order(40)
    void markLostAndFound() {
        var lostResult = service.markLost(itemId, null, null);
        assertTrue(lostResult.isPresent());
        assertNotNull(lostResult.get().lostAt());

        var foundResult = service.markFound(itemId);
        assertTrue(foundResult.isPresent());
        assertNull(foundResult.get().lostAt());
    }

    @Test
    @Order(50)
    void deleteItem() {
        assertTrue(service.deleteItem(itemId, null));
        assertTrue(service.findItemById(itemId).isEmpty());
    }

    @Test
    @Order(55)
    void findItemById() {
        // Item was deleted in order 50, so create a new one
        var inv = service.create(station.id(), "FindItem Inv", InventoryType.INTERNAL, false, true);
        var item = service.createItem(inv.id(), "FI-001", "FindItem 1", null, null);
        assertTrue(service.findItemById(item.id()).isPresent());
        assertTrue(service.findItemById(99999).isEmpty());
        service.delete(inv.id());
    }

    @Test
    @Order(56)
    void updateItem() {
        var inv = service.create(station.id(), "UpdateItem Inv", InventoryType.INTERNAL, false, true);
        var item = service.createItem(inv.id(), "UI-001", "Original Name", null, null);
        var updated =
                service.updateItem(item.id(), "UI-002", "Updated Name", null, InventoryItemMetadata.empty(), null);
        assertTrue(updated.isPresent());
        assertEquals("Updated Name", updated.get().name());
        assertEquals("UI-002", updated.get().internalId());
        service.delete(inv.id());
    }

    @Test
    @Order(57)
    void updateItemNonExistent() {
        assertTrue(service.updateItem(99999, "XX", "XX", null, null, null).isEmpty());
    }

    @Test
    @Order(58)
    void updateInventory() {
        var inv = service.create(station.id(), "ToUpdate", InventoryType.INTERNAL, true, true);
        var updated = service.update(inv.id(), "Updated Inv", InventoryType.EXTERNAL, false, true);
        assertTrue(updated.isPresent());
        assertEquals("Updated Inv", updated.get().name());
        assertEquals(InventoryType.EXTERNAL, updated.get().inventoryType());
        service.delete(inv.id());
    }

    @Test
    @Order(59)
    void updateInventoryNonExistent() {
        assertTrue(service.update(99999, "Nope", InventoryType.INTERNAL, false, true)
                .isEmpty());
    }

    @Test
    @Order(60)
    void deleteInventory() {
        assertTrue(service.delete(inventoryId));
        assertTrue(service.findById(inventoryId).isEmpty());
    }

    @Test
    @Order(61)
    void updateSizeAndDeleteSize() {
        var inv = service.create(station.id(), "SizeTest Inv", InventoryType.INTERNAL, true, true);
        service.createSize(inv.id(), "S", 0, null);
        var sizes = service.findSizes(inv.id());
        var sizeId = sizes.getFirst().id();

        var updated = service.updateSize(inv.id(), sizeId, "Small", 1, "note");
        assertTrue(updated.isPresent());
        assertTrue(updated.get().stream().anyMatch(s -> "Small".equals(s.label())));

        var deleted = service.deleteSize(inv.id(), sizeId);
        assertTrue(deleted.isPresent());
        assertTrue(deleted.get().isEmpty());

        service.delete(inv.id());
    }

    @Test
    @Order(62)
    void updateSizeNonExistent() {
        assertTrue(service.updateSize(99999, 99999, "X", 0, null).isEmpty());
    }

    @Test
    @Order(63)
    void deleteSizeNonExistent() {
        assertTrue(service.deleteSize(99999, 99999).isEmpty());
    }

    @Test
    @Order(64)
    void findAllItemsByStation() {
        var inv = service.create(station.id(), "AllItems Inv", InventoryType.INTERNAL, false, true);
        service.createItem(inv.id(), "AI-001", "All Item 1", null, null);
        var items = service.findAllItemsByStation(station.id());
        assertFalse(items.isEmpty());
        service.delete(inv.id());
    }

    @Test
    @Order(65)
    void findAllSizesByStation() {
        var inv = service.create(station.id(), "AllSizes Inv", InventoryType.INTERNAL, true, true);
        service.createSize(inv.id(), "XL", 0, null);
        var sizes = service.findAllSizesByStation(station.id());
        assertFalse(sizes.isEmpty());
        service.delete(inv.id());
    }

    @Test
    @Order(66)
    void findHistory() {
        var inv = service.create(station.id(), "History Inv", InventoryType.INTERNAL, false, true);
        var item = service.createItem(inv.id(), "HI-001", "History Item", null, null);
        service.assignItem(item.id(), member.id(), "Inv Tester");
        service.assignItem(item.id(), null, "");
        var history = service.findHistory(item.id());
        assertFalse(history.isEmpty());
        service.delete(inv.id());
    }

    @Test
    @Order(67)
    void markLostNonExistent() {
        assertTrue(service.markLost(99999, null, null).isEmpty());
    }

    @Test
    @Order(68)
    void markFoundNonExistent() {
        assertTrue(service.markFound(99999).isEmpty());
    }

    @Test
    @Order(69)
    void assignItemNonExistent() {
        assertTrue(service.assignItem(99999, member.id(), "").isEmpty());
    }

    @Test
    @Order(70)
    void createItemWithOwner() {
        var inv = service.create(station.id(), "Owner Inv", InventoryType.MIXED, false, true);
        var item = service.createItem(inv.id(), "SI-001", "Owner Item", null, null, ItemOwner.CLUSTER, null);
        assertNotNull(item);
        assertEquals(ItemOwner.CLUSTER, item.ownerKind());
        assertFalse(item.ownedByStation());

        var own = service.createItem(inv.id(), "SI-002", "Station Item", null, null, ItemOwner.STATION, null);
        assertTrue(own.ownedByStation());
        service.delete(inv.id());
    }

    @Test
    @Order(71)
    void requirementCrud() {
        var inv = service.create(station.id(), "Req Inv", InventoryType.INTERNAL, false, true);
        // Use MEMBER user type
        var req = service.createRequirement(inv.id(), StationUserType.MEMBER, 0, null, 3);
        assertNotNull(req);

        assertTrue(service.updateRequirement(req.id(), 5));
        assertTrue(service.updateRequirementPosition(req.id(), 2));
        assertTrue(service.deleteRequirement(req.id()));

        var reqs = service.findAllRequirementsByStation(station.id());
        assertNotNull(reqs);
        service.delete(inv.id());
    }

    /**
     * A requirement of the association's may name a group of its stations, and then counts at the stations
     * in that group and at no others. One naming no group counts everywhere, which is what every row wrote
     * before the column existed.
     */
    @Test
    @Order(73)
    void anAssociationsRequirementCanBeAimedAtAGroupOfStations() {
        var home = stationRepo.create("Träger Gruppen");
        var cluster = clusterRepo.create("Kreisverband Gruppen", null, home.id());
        clusterRepo.setUsesInventory(cluster.id(), true);
        stationRepo.setCluster(station.id(), cluster.id());

        var theirs = service.create(home.id(), "Bootsausrüstung", InventoryType.INTERNAL, false, true);
        var group = clusterStationGroupRepo.create(cluster.id(), "Wasserwachen " + station.id());

        service.createRequirement(theirs.id(), StationUserType.MEMBER, 0, group.id(), 1);
        assertTrue(
                service.findRequirementsVisibleAt(station.id()).stream().noneMatch(row -> row.fromCluster()),
                "a station outside the group is asked for nothing");

        clusterStationGroupRepo.setStations(group.id(), List.of(station.id()));
        assertTrue(
                service.findRequirementsVisibleAt(station.id()).stream().anyMatch(row -> row.fromCluster()),
                "and inside it, the same requirement counts");
        assertTrue(
                service.findRequirementsVisibleAt(station.id()).stream().anyMatch(row -> Integer.valueOf(group.id())
                        .equals(row.requirement().stationGroupId())),
                "the row says which group it was written for");

        var otherHome = stationRepo.create("Träger Fremd");
        var otherCluster = clusterRepo.create("Kreisverband Fremd", null, otherHome.id());
        var otherGroup = clusterStationGroupRepo.create(otherCluster.id(), "Fremde Gruppe");
        assertThrows(
                BadRequestResponse.class,
                () -> service.createRequirement(theirs.id(), StationUserType.MEMBER, 0, otherGroup.id(), 1),
                "and no association can aim a requirement with another's filing");

        stationRepo.setCluster(station.id(), null);
        service.delete(theirs.id());
        clusterStationGroupRepo.delete(otherGroup.id());
        clusterStationGroupRepo.setStations(group.id(), List.of());
        clusterStationGroupRepo.delete(group.id());
        clusterRepo.delete(otherCluster.id());
        clusterRepo.delete(cluster.id());
        stationRepo.delete(otherHome.id());
        stationRepo.delete(home.id());
    }

    /**
     * A station under a cluster reads what the cluster asks of its people beside what it asks itself, and
     * the cluster's rows say whose they are so the screen can badge them and take the controls away.
     */
    @Test
    @Order(72)
    void requirementsVisibleAtAStationCarryTheClustersOwn() {
        var home = stationRepo.create("Träger Vorgaben");
        var cluster = clusterRepo.create("Kreisverband Vorgaben", null, home.id());
        // A cluster keeps no gear here until it says so, and one that keeps none asks nothing either
        clusterRepo.setUsesInventory(cluster.id(), true);
        stationRepo.setCluster(station.id(), cluster.id());

        var mine = service.create(station.id(), "Eigene Vorgabe", InventoryType.INTERNAL, false, true);
        var theirs = service.create(home.id(), "Verbandsvorgabe", InventoryType.INTERNAL, false, true);
        service.createRequirement(mine.id(), StationUserType.MEMBER, 0, null, 1);
        service.createRequirement(theirs.id(), StationUserType.MEMBER, 0, null, 2);

        var visible = service.findRequirementsVisibleAt(station.id());
        var fromCluster = visible.stream()
                .filter(InventoryRepository.VisibleRequirement::fromCluster)
                .toList();
        assertEquals(1, fromCluster.size(), "the cluster's one requirement, named as the cluster's");
        assertEquals("Verbandsvorgabe", fromCluster.getFirst().inventoryName());
        assertEquals(2, fromCluster.getFirst().requirement().quantity());

        assertTrue(
                visible.stream().anyMatch(row -> !row.fromCluster() && "Eigene Vorgabe".equals(row.inventoryName())),
                "and the station's own beside it");

        assertEquals("Kreisverband Vorgaben", service.ownerAbove(station.id()).orElse(null));

        // A cluster that does not keep its gear here asks nothing of anybody
        clusterRepo.setUsesInventory(cluster.id(), false);
        assertTrue(service.findRequirementsVisibleAt(station.id()).stream().noneMatch(row -> row.fromCluster()));
        assertTrue(service.ownerAbove(station.id()).isEmpty());

        stationRepo.setCluster(station.id(), null);
        service.delete(mine.id());
        service.delete(theirs.id());
        clusterRepo.delete(cluster.id());
        stationRepo.delete(home.id());
    }

    /** A station under nobody reads only its own, and there is no name to put on them. */
    @Test
    @Order(73)
    void requirementsVisibleAtAStationUnderNobodyAreItsOwn() {
        var inv = service.create(station.id(), "Allein", InventoryType.INTERNAL, false, true);
        service.createRequirement(inv.id(), StationUserType.MEMBER, 0, null, 1);

        assertTrue(service.findRequirementsVisibleAt(station.id()).stream().noneMatch(row -> row.fromCluster()));
        assertTrue(service.ownerAbove(station.id()).isEmpty());

        service.delete(inv.id());
    }
}
