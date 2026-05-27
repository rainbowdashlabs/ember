/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
        service = new InventoryService(inventoryRepo);
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
        var inv = service.create(station.id(), "Helmets", InventoryType.INTERNAL, true);
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
        var item = service.createItem(inventoryId, "H-001", "Helmet 1", sizeId, "{}");
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

    @Test
    @Order(40)
    void markLostAndFound() {
        var lostResult = service.markLost(itemId);
        assertTrue(lostResult.isPresent());
        assertNotNull(lostResult.get().lostAt());

        var foundResult = service.markFound(itemId);
        assertTrue(foundResult.isPresent());
        assertNull(foundResult.get().lostAt());
    }

    @Test
    @Order(50)
    void deleteItem() {
        assertTrue(service.deleteItem(itemId));
        assertTrue(service.findItemById(itemId).isEmpty());
    }

    @Test
    @Order(55)
    void findItemById() {
        // Item was deleted in order 50, so create a new one
        var inv = service.create(station.id(), "FindItem Inv", InventoryType.INTERNAL, false);
        var item = service.createItem(inv.id(), "FI-001", "FindItem 1", null, "{}");
        assertTrue(service.findItemById(item.id()).isPresent());
        assertTrue(service.findItemById(99999).isEmpty());
        service.delete(inv.id());
    }

    @Test
    @Order(56)
    void updateItem() {
        var inv = service.create(station.id(), "UpdateItem Inv", InventoryType.INTERNAL, false);
        var item = service.createItem(inv.id(), "UI-001", "Original Name", null, "{}");
        var updated = service.updateItem(item.id(), "UI-002", "Updated Name", null, "{\"color\":\"red\"}");
        assertTrue(updated.isPresent());
        assertEquals("Updated Name", updated.get().name());
        assertEquals("UI-002", updated.get().internalId());
        service.delete(inv.id());
    }

    @Test
    @Order(57)
    void updateItemNonExistent() {
        assertTrue(service.updateItem(99999, "XX", "XX", null, "{}").isEmpty());
    }

    @Test
    @Order(58)
    void updateInventory() {
        var inv = service.create(station.id(), "ToUpdate", InventoryType.INTERNAL, true);
        var updated = service.update(inv.id(), "Updated Inv", InventoryType.EXTERNAL, false);
        assertTrue(updated.isPresent());
        assertEquals("Updated Inv", updated.get().name());
        assertEquals(InventoryType.EXTERNAL, updated.get().inventoryType());
        service.delete(inv.id());
    }

    @Test
    @Order(59)
    void updateInventoryNonExistent() {
        assertTrue(service.update(99999, "Nope", InventoryType.INTERNAL, false).isEmpty());
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
        var inv = service.create(station.id(), "SizeTest Inv", InventoryType.INTERNAL, true);
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
        var inv = service.create(station.id(), "AllItems Inv", InventoryType.INTERNAL, false);
        service.createItem(inv.id(), "AI-001", "All Item 1", null, "{}");
        var items = service.findAllItemsByStation(station.id());
        assertFalse(items.isEmpty());
        service.delete(inv.id());
    }

    @Test
    @Order(65)
    void findAllSizesByStation() {
        var inv = service.create(station.id(), "AllSizes Inv", InventoryType.INTERNAL, true);
        service.createSize(inv.id(), "XL", 0, null);
        var sizes = service.findAllSizesByStation(station.id());
        assertFalse(sizes.isEmpty());
        service.delete(inv.id());
    }

    @Test
    @Order(66)
    void findHistory() {
        var inv = service.create(station.id(), "History Inv", InventoryType.INTERNAL, false);
        var item = service.createItem(inv.id(), "HI-001", "History Item", null, "{}");
        service.assignItem(item.id(), member.id(), "Inv Tester");
        service.assignItem(item.id(), null, "");
        var history = service.findHistory(item.id());
        assertFalse(history.isEmpty());
        service.delete(inv.id());
    }

    @Test
    @Order(67)
    void markLostNonExistent() {
        assertTrue(service.markLost(99999).isEmpty());
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
    void createItemWithSource() {
        var inv = service.create(station.id(), "Source Inv", InventoryType.INTERNAL, false);
        var item = service.createItem(inv.id(), "SI-001", "Source Item", null, "{}", InventoryItem.ItemSource.EXTERNAL);
        assertNotNull(item);
        service.delete(inv.id());
    }

    @Test
    @Order(71)
    void requirementCrud() {
        var inv = service.create(station.id(), "Req Inv", InventoryType.INTERNAL, false);
        // Need a real role ID — use the MEMBER role
        var memberRole = stationMemberRepo.findRoleByName(Roles.MEMBER).orElseThrow();
        var req = service.createRequirement(inv.id(), memberRole.id(), 0, 3);
        assertNotNull(req);

        assertTrue(service.updateRequirement(req.id(), 5));
        assertTrue(service.updateRequirementPosition(req.id(), 2));
        assertTrue(service.deleteRequirement(req.id()));

        var reqs = service.findAllRequirementsByStation(station.id());
        assertNotNull(reqs);
        service.delete(inv.id());
    }
}
