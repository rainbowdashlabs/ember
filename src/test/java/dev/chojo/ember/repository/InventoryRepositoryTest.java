/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
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
        assertTrue(inventoryRepo.update(inventoryId, "Jackets", InventoryType.INTERNAL, false));
        Inventory updated = inventoryRepo.findById(inventoryId).orElseThrow();
        assertEquals("Jackets", updated.name());
        assertFalse(updated.hasSizes());
        // restore
        inventoryRepo.update(inventoryId, "Helmets", InventoryType.EXTERNAL, true);
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
    @Order(23)
    void updateItem() {
        assertTrue(inventoryRepo.updateItem(itemId, "H-002", "Blue Helmet", sizeId, new InventoryItemMetadata(true)));
        InventoryItem updated = inventoryRepo.findItemById(itemId).orElseThrow();
        assertEquals("H-002", updated.internalId());
        assertEquals("Blue Helmet", updated.name());
    }

    @Test
    @Order(24)
    void assignItem() {
        assertTrue(inventoryRepo.assignItem(itemId, member.id()));
        assertEquals(
                member.id(), inventoryRepo.findItemById(itemId).orElseThrow().assignedTo());

        assertFalse(inventoryRepo.findItemsByMember(member.id()).isEmpty());
        assertEquals(1, inventoryRepo.countItemsByMember(member.id()));

        // Unassign
        assertTrue(inventoryRepo.assignItem(itemId, null));
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
    private static MemberGroup group;

    @Test
    @Order(40)
    void createRequirementByUserType() {
        InventoryRequirement req = inventoryRepo.createRequirement(inventoryId, StationUserType.MEMBER, 0, 2);
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
        group = memberGroupRepo.create(station.id(), "Req Group");
        InventoryRequirement req = inventoryRepo.createRequirement(inventoryId, null, group.id(), 3);
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
        assertTrue(inventoryRepo.markLost(item.id()));
        assertNotNull(inventoryRepo.findItemById(item.id()).orElseThrow().lostAt());

        assertTrue(inventoryRepo.markFound(item.id()));
        assertNull(inventoryRepo.findItemById(item.id()).orElseThrow().lostAt());

        inventoryRepo.deleteItem(item.id());
    }

    // -- createItem with ItemSource --

    @Test
    @Order(49)
    void createItemWithItemSource() {
        InventoryItem item = inventoryRepo.createItem(
                inventoryId, "SRC-001", "Sourced Item", null, null, InventoryItem.ItemSource.EXTERNAL);
        assertNotNull(item);
        assertEquals(InventoryItem.ItemSource.EXTERNAL, item.itemSource());
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
        var req = inventoryRepo.createRequirement(inventoryId, StationUserType.TEAM, 0, 1);
        assertTrue(inventoryRepo.updateRequirementPosition(req.id(), 5));
        inventoryRepo.deleteRequirement(req.id());
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
