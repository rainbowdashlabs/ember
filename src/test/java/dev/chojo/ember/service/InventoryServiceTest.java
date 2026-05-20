/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
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
    @Order(60)
    void deleteInventory() {
        assertTrue(service.delete(inventoryId));
        assertTrue(service.findById(inventoryId).isEmpty());
    }
}
