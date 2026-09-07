/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static int sizeId;
    private static int itemId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Inv Station");
        account = accountRepo.create("inv@test.com", "Inv", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        Inventory inv = inventoryRepo.create(station.id(), "Helmets", InventoryType.EXTERNAL, true);
        assertNotNull(inv);
        assertEquals("Helmets", inv.name());
        assertTrue(inv.hasSizes());
        inventoryId = inv.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(inventoryRepo.findById(inventoryId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        assertEquals(1, inventoryRepo.findByStation(station.id()).size());
    }

    @Test
    @Order(4)
    void update() {
        assertTrue(inventoryRepo.update(inventoryId, "Jackets", InventoryType.INTERNAL, false, true));
        Inventory updated = inventoryRepo.findById(inventoryId).orElseThrow();
        assertEquals("Jackets", updated.name());
        assertFalse(updated.hasSizes());
        assertTrue(updated.homogeneous());
        // restore
        inventoryRepo.update(inventoryId, "Helmets", InventoryType.EXTERNAL, true, true);
    }

    // -- Sizes --

    @Test
    @Order(10)
    void createSize() {
        inventoryRepo.createSize(inventoryId, "Small", 1, null);
        inventoryRepo.createSize(inventoryId, "Large", 2, null);
        var sizes = inventoryRepo.findSizes(inventoryId);
        assertEquals(2, sizes.size());
        assertEquals("Small", sizes.getFirst().label());
        sizeId = sizes.getFirst().id();
    }

    @Test
    @Order(11)
    void updateSize() {
        assertTrue(inventoryRepo.updateSize(sizeId, "Medium", 1, null));
        assertEquals("Medium", inventoryRepo.findSizes(inventoryId).getFirst().label());
    }

    @Test
    @Order(12)
    void deleteSize() {
        int secondId = inventoryRepo.findSizes(inventoryId).get(1).id();
        assertTrue(inventoryRepo.deleteSize(secondId));
        assertEquals(1, inventoryRepo.findSizes(inventoryId).size());
    }

    // -- Items --

    @Test
    @Order(19)
    void findSummariesByStation() {
        var summaries = inventoryRepo.findSummariesByStation(station.id());
        assertTrue(summaries.stream().anyMatch(s -> s.id() == inventoryId));
    }

    /**
     * The number a collection shows beside its name counts the kinds written down for it, not the
     * kinds its pieces happen to carry, so a kind nobody owns a piece of still counts and a piece
     * lying there without one does not lower it.
     */
    @Test
    @Order(98)
    void summaryCountsTheKindsDefinedInAnInventory() {
        var collection = inventoryRepo.create(station.id(), "Funkkiste", InventoryType.INTERNAL, false, false);
        artRepo.create(collection.id(), "Funkgerät", "", 0);
        artRepo.create(collection.id(), "Ladegerät", "", 10);
        inventoryRepo.createItem(collection.id(), null, "Antenne", null, null);

        var summary = inventoryRepo.findSummariesByStation(station.id()).stream()
                .filter(s -> s.id() == collection.id())
                .findFirst()
                .orElseThrow();

        assertEquals(2, summary.artCount());
        assertEquals(1, summary.itemCount());
    }

    @Test
    @Order(20)
    void createItem() {
        InventoryItem item = inventoryRepo.createItem(inventoryId, "H-001", "Red Helmet", sizeId, null);
        assertNotNull(item);
        assertEquals("H-001", item.internalId());
        itemId = item.id();
    }

    @Test
    @Order(21)
    void findItemById() {
        assertTrue(inventoryRepo.findItemById(itemId).isPresent());
    }

    @Test
    @Order(22)
    void findItems() {
        assertEquals(1, inventoryRepo.findItems(inventoryId).size());
    }

    @Test
    @Order(21)
    void findByInternalId() {
        assertTrue(inventoryRepo.findByInternalId(station.id(), "H-001").isPresent());
        assertTrue(inventoryRepo.findByInternalId(station.id(), "NO-SUCH-ID").isEmpty());
    }

    @Test
    @Order(23)
    void updateItem() {
        assertTrue(
                inventoryRepo.updateItem(itemId, "H-002", "Blue Helmet", sizeId, null, InventoryItemMetadata.empty()));
        InventoryItem updated = inventoryRepo.findItemById(itemId).orElseThrow();
        assertEquals("H-002", updated.internalId());
        assertEquals("Blue Helmet", updated.name());
    }

    @Test
    @Order(24)
    void assignItem() {
        assertTrue(itemCustodyService.assignToMember(itemId, member.id(), "").isPresent());
        assertEquals(
                member.id(), inventoryRepo.findItemById(itemId).orElseThrow().assignedTo());

        assertFalse(inventoryRepo.findItemsByMember(member.id()).isEmpty());
        assertEquals(1, inventoryRepo.countItemsByMember(member.id()));

        // Unassign
        assertTrue(itemCustodyService.takeBack(itemId).isPresent());
        assertNull(inventoryRepo.findItemById(itemId).orElseThrow().assignedTo());
        assertEquals(0, inventoryRepo.countItemsByMember(member.id()));
    }

    // -- History --

    @Test
    @Order(30)
    void createHistory() {
        // Re-create item for history tests
        InventoryItem item = inventoryRepo.createItem(inventoryId, "H-003", "History Helmet", sizeId, null);
        itemId = item.id();

        InventoryItemHistory history = inventoryRepo.createHistory(itemId, member.id(), "Inv User");
        assertNotNull(history);
        assertEquals(itemId, history.itemId());
        assertEquals(member.id(), history.memberId());
        assertEquals("Inv User", history.memberName());
        assertNotNull(history.givenOut());
        assertNull(history.returned());
    }

    @Test
    @Order(31)
    void findHistory() {
        var history = inventoryRepo.findHistory(itemId);
        assertEquals(1, history.size());
        assertEquals("Inv User", history.getFirst().memberName());
    }

    @Test
    @Order(32)
    void returnHistory() {
        assertTrue(inventoryRepo.returnHistory(itemId, member.id()));
        var history = inventoryRepo.findHistory(itemId);
        assertNotNull(history.getFirst().returned());
    }

    @Test
    @Order(33)
    void returnHistoryAlreadyReturned() {
        assertFalse(inventoryRepo.returnHistory(itemId, member.id()));
    }

    @Test
    @Order(34)
    void cleanupHistoryItem() {
        assertTrue(inventoryRepo.deleteItem(itemId));
    }

    // -- Requirements --

    private static int requirementId;

    @Test
    @Order(40)
    void createRequirementByUserType() {
        InventoryRequirement req = inventoryRepo.createRequirement(inventoryId, StationUserType.MEMBER, 0, null, 2);
        assertNotNull(req);
        assertEquals(inventoryId, req.inventoryId());
        assertEquals(2, req.quantity());
        requirementId = req.id();
    }

    @Test
    @Order(41)
    void findAllRequirementsByStation() {
        var reqs = inventoryRepo.findAllRequirementsByStation(station.id());
        assertEquals(1, reqs.size());
        assertEquals(2, reqs.getFirst().quantity());
    }

    @Test
    @Order(42)
    void updateRequirement() {
        assertTrue(inventoryRepo.updateRequirement(requirementId, 5));
        var reqs = inventoryRepo.findAllRequirementsByStation(station.id());
        assertEquals(5, reqs.getFirst().quantity());
    }

    @Test
    @Order(43)
    void deleteRequirement() {
        assertTrue(inventoryRepo.deleteRequirement(requirementId));
        assertTrue(inventoryRepo.findAllRequirementsByStation(station.id()).isEmpty());
    }

    @Test
    @Order(44)
    void createRequirementByGroup() {
        MemberGroup group = memberGroupRepo.create(station.id(), "Req Group");
        InventoryRequirement req = inventoryRepo.createRequirement(inventoryId, null, group.id(), null, 3);
        assertNotNull(req);
        assertEquals(group.id(), req.groupId());
        assertEquals(3, req.quantity());
        // cleanup
        inventoryRepo.deleteRequirement(req.id());
        memberGroupRepo.delete(group.id());
    }

    // -- findItemsByStation / findSizesByStation / findUnassignedItems --

    @Test
    @Order(45)
    void findItemsByStation() {
        // Create a fresh item for this test
        InventoryItem item = inventoryRepo.createItem(inventoryId, "STAT-001", "Station Item", sizeId, null);
        var items = inventoryRepo.findItemsByStation(station.id());
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.id() == item.id()));
        inventoryRepo.deleteItem(item.id());
    }

    @Test
    @Order(46)
    void findSizesByStation() {
        var sizes = inventoryRepo.findSizesByStation(station.id());
        assertFalse(sizes.isEmpty());
    }

    @Test
    @Order(47)
    void findUnassignedItems() {
        InventoryItem item = inventoryRepo.createItem(inventoryId, "UNAS-001", "Unassigned Item", null, null);
        var unassigned = inventoryRepo.findUnassignedItems(inventoryId);
        assertFalse(unassigned.isEmpty());
        assertTrue(unassigned.stream().anyMatch(i -> i.id() == item.id()));
        inventoryRepo.deleteItem(item.id());
    }

    // -- markLost / markFound --

    @Test
    @Order(48)
    void markLostAndFound() {
        InventoryItem item = inventoryRepo.createItem(inventoryId, "LOST-001", "Lost Item", null, null);
        assertTrue(itemCustodyService.markLost(item.id(), null, null).isPresent());
        assertNotNull(inventoryRepo.findItemById(item.id()).orElseThrow().lostAt());

        assertTrue(itemCustodyService.markFound(item.id()).isPresent());
        assertNull(inventoryRepo.findItemById(item.id()).orElseThrow().lostAt());

        inventoryRepo.deleteItem(item.id());
    }

    // -- createItem with an owner --

    @Test
    @Order(49)
    void createItemWithOwner() {
        InventoryItem item =
                inventoryRepo.createItem(inventoryId, "SRC-001", "Owned Item", null, null, ItemOwner.CLUSTER, null);
        assertNotNull(item);
        assertEquals(ItemOwner.CLUSTER, item.ownerKind());
        assertNull(item.ownerClusterId());
        assertFalse(item.ownedByStation());
        inventoryRepo.deleteItem(item.id());
    }

    @Test
    @Order(50)
    void createItemDefaultsToTheStation() {
        InventoryItem item = inventoryRepo.createItem(inventoryId, "SRC-002", "Station Item", null, null);
        assertEquals(ItemOwner.STATION, item.ownerKind());
        assertNull(item.ownerClusterId());
        assertTrue(item.ownedByStation());
        inventoryRepo.deleteItem(item.id());
    }

    @Test
    @Order(51)
    void stationOwnedItemNeverCarriesACluster() {
        InventoryItem item =
                inventoryRepo.createItem(inventoryId, "SRC-003", "Station Item", null, null, ItemOwner.STATION, 7);
        assertNull(item.ownerClusterId());
        inventoryRepo.deleteItem(item.id());
    }

    // -- createHistoryWithDates --

    @Test
    @Order(35)
    void createHistoryWithDates() {
        InventoryItem item = inventoryRepo.createItem(inventoryId, "HIST-D", "HistDates Item", null, null);
        Instant givenOut = Instant.parse("2025-01-01T10:00:00Z");
        Instant returned = Instant.parse("2025-01-15T10:00:00Z");
        assertDoesNotThrow(
                () -> inventoryRepo.createHistoryWithDates(item.id(), member.id(), "Inv User", givenOut, returned));
        var history = inventoryRepo.findHistory(item.id());
        assertEquals(1, history.size());
        assertNotNull(history.getFirst().returned());
        inventoryRepo.deleteItem(item.id());
    }

    // -- updateRequirementPosition --

    @Test
    @Order(43)
    void updateRequirementPosition() {
        var req = inventoryRepo.createRequirement(inventoryId, StationUserType.TEAM, 0, null, 1);
        assertTrue(inventoryRepo.updateRequirementPosition(req.id(), 5));
        inventoryRepo.deleteRequirement(req.id());
    }

    /**
     * What an association owns, counted where the pieces are rather than fetched row by row. A piece the
     * station bought itself is in nobody's count here, which is the point of asking by owner.
     */
    @Test
    @Order(45)
    void countItemsOwnedByCluster() {
        var home = stationRepo.create("Träger Zählung");
        var cluster = clusterRepo.create("Kreisverband Zählung", null, home.id());
        var jackets = inventoryRepo.create(station.id(), "Jacken Zählung", InventoryType.EXTERNAL, true);
        inventoryRepo.createSize(jackets.id(), "52", 0, null);
        int size = inventoryRepo.findSizes(jackets.id()).getFirst().id();
        inventoryRepo.createItem(jackets.id(), "JZ-1", "Jacke", size, null, ItemOwner.CLUSTER, cluster.id());
        inventoryRepo.createItem(jackets.id(), "JZ-2", "Jacke", size, null, ItemOwner.CLUSTER, cluster.id());
        inventoryRepo.createItem(jackets.id(), "JZ-3", "Jacke", size, null, ItemOwner.STATION, null);

        var counts = inventoryRepo.countItemsOwnedByCluster(cluster.id());

        assertEquals(1, counts.size(), "one row per kind and size");
        var row = counts.getFirst();
        assertEquals(jackets.id(), row.inventoryId());
        assertEquals("Jacken Zählung", row.inventoryName());
        assertEquals("52", row.sizeLabel());
        assertEquals(2, row.total(), "the station's own jacket is not the association's to count");
        assertEquals(2, row.atStation(), "gear a station recorded for the body above it is held at that station");
        assertEquals(0, row.inStore());
        assertEquals(0, row.lost());

        inventoryRepo.delete(jackets.id());
        clusterRepo.delete(cluster.id());
        stationRepo.delete(home.id());
    }

    // -- Cleanup --

    @Test
    @Order(50)
    void deleteItemFinal() {
        // item was already deleted in Order(34)
        assertFalse(inventoryRepo.deleteItem(itemId));
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(inventoryRepo.delete(inventoryId));
    }
}
