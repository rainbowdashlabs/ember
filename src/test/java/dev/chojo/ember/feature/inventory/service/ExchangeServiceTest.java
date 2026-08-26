/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
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
class ExchangeServiceTest extends RepositoryTestBase {
    private static ExchangeService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static int itemId;
    private static int exchangeId;

    @BeforeAll
    static void setup() {
        var inventoryService =
                new InventoryService(inventoryRepo, itemCustodyService, clusterRepo, clusterStationGroupRepo);
        service = new ExchangeService(itemMovementService, inventoryRepo);
        station = stationRepo.create("ExchStation");
        account = accountRepo.create("exch-svc@test.com", "Exch", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
        var inv = inventoryRepo.create(station.id(), "Blouson", InventoryType.INTERNAL, true);
        inventoryId = inv.id();
        inventoryRepo.createSize(inv.id(), "M", 0, null);
        var sizes = inventoryRepo.findSizes(inv.id());
        var sizeM =
                sizes.stream().filter(s -> "M".equals(s.label())).findFirst().orElseThrow();
        var item = inventoryRepo.createItem(inv.id(), "B-001", "Blouson M", sizeM.id(), null);
        itemId = item.id();
        itemCustodyService.assignToMember(item.id(), member.id(), "");
    }

    @AfterAll
    static void cleanup() {
        inventoryRepo.delete(inventoryId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createExchange() {
        inventoryRepo.createSize(inventoryId, "L", 1, null);
        var sizes = inventoryRepo.findSizes(inventoryId);
        var sizeL =
                sizes.stream().filter(s -> "L".equals(s.label())).findFirst().orElseThrow();
        var sizeM =
                sizes.stream().filter(s -> "M".equals(s.label())).findFirst().orElseThrow();
        var exchange = service.create(
                station.id(),
                member.id(),
                "Exch Tester",
                itemId,
                inventoryId,
                sizeM.id(),
                sizeL.id(),
                "Too small",
                null);
        assertNotNull(exchange);
        assertEquals(ExchangeStatus.ANNOUNCED, exchange.status());
        exchangeId = exchange.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(exchangeId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        var list = service.findByStation(station.id());
        assertTrue(list.stream().anyMatch(e -> e.id() == exchangeId));
    }

    @Test
    @Order(4)
    void findByMember() {
        var list = service.findByMember(member.id());
        assertTrue(list.stream().anyMatch(e -> e.id() == exchangeId));
    }

    @Test
    @Order(10)
    void updateStatusWalksAsFarAsTheFlowGoes() {
        // Gear the station owns has no shipping leg to reach: it is taken back and handed out again,
        // so asking for a status the chain does not contain gets as far as the chain goes
        var updated = service.updateStatus(exchangeId, ExchangeStatus.SHIPPED, member.id(), null);
        assertNotNull(updated);
        assertEquals(ExchangeStatus.RECEIVED, updated.status());
    }

    @Test
    @Order(11)
    void findLogs() {
        var logs = service.findLogs(exchangeId);
        assertFalse(logs.isEmpty());
    }

    @Test
    @Order(15)
    void updateStatusToExchanged() {
        // Create a new item to serve as the exchange replacement
        var sizes = inventoryRepo.findSizes(inventoryId);
        var sizeL =
                sizes.stream().filter(s -> "L".equals(s.label())).findFirst().orElseThrow();
        var newItem = inventoryRepo.createItem(inventoryId, "B-002", "Blouson L", sizeL.id(), null);

        var updated = service.updateStatus(exchangeId, ExchangeStatus.DONE, member.id(), "Completed", newItem.id());
        assertNotNull(updated);
        assertEquals(ExchangeStatus.DONE, updated.status());
    }

    @Test
    @Order(20)
    void cancel() {
        // Create a new exchange for delete testing since the previous one was DONE
        var sizes = inventoryRepo.findSizes(inventoryId);
        var sizeM =
                sizes.stream().filter(s -> "M".equals(s.label())).findFirst().orElseThrow();
        var exchange = service.create(
                station.id(), member.id(), "Exch Tester", null, inventoryId, sizeM.id(), null, "Delete test", null);
        assertTrue(service.delete(exchange.id()));
        assertTrue(service.findById(exchange.id()).isEmpty());
    }

    @Test
    @Order(21)
    void completingAnExchangeOfOwnerOwnedGearDoesNotMakeItTheStations() {
        var mixed = inventoryRepo.create(station.id(), "Handschuhe (Träger)", InventoryType.MIXED, false);
        var ownerItem = inventoryRepo.createItem(mixed.id(), "HS-C", "Glove", null, null, ItemOwner.CLUSTER, null);
        itemCustodyService.assignToMember(ownerItem.id(), member.id(), "");
        var replacement = inventoryRepo.createItem(mixed.id(), "HS-C2", "Glove", null, null, ItemOwner.CLUSTER, null);

        var exchange = service.create(
                station.id(), member.id(), "Exch Tester", ownerItem.id(), mixed.id(), null, null, "Worn", null);
        service.updateStatus(exchange.id(), ExchangeStatus.DONE, member.id(), "Completed", replacement.id());

        // The old item goes back to the body above rather than turning up in the station's stock
        assertEquals(
                ItemCustody.WITH_OWNER,
                inventoryRepo.findItemById(ownerItem.id()).orElseThrow().custody());
        assertFalse(inventoryRepo.findUnassignedItems(mixed.id()).stream().anyMatch(i -> i.id() == ownerItem.id()));
        assertFalse(inventoryRepo.findItemsByStation(station.id()).stream().anyMatch(i -> i.id() == ownerItem.id()));
        assertEquals(
                member.id(),
                inventoryRepo.findItemById(replacement.id()).orElseThrow().assignedTo());

        inventoryRepo.delete(mixed.id());
    }

    @Test
    @Order(22)
    void completingAnExchangeOfStationOwnedGearReturnsItToTheFreePool() {
        var mixed = inventoryRepo.create(station.id(), "Handschuhe (Wache)", InventoryType.MIXED, false);
        var ownItem = inventoryRepo.createItem(mixed.id(), "HS-S", "Glove", null, null, ItemOwner.STATION, null);
        itemCustodyService.assignToMember(ownItem.id(), member.id(), "");
        var replacement = inventoryRepo.createItem(mixed.id(), "HS-S2", "Glove", null, null, ItemOwner.STATION, null);

        var exchange = service.create(
                station.id(), member.id(), "Exch Tester", ownItem.id(), mixed.id(), null, null, "Worn", null);
        service.updateStatus(exchange.id(), ExchangeStatus.DONE, member.id(), "Completed", replacement.id());

        var old = inventoryRepo.findItemById(ownItem.id()).orElseThrow();
        assertNull(old.assignedTo());
        assertTrue(inventoryRepo.findUnassignedItems(mixed.id()).stream().anyMatch(i -> i.id() == ownItem.id()));

        inventoryRepo.delete(mixed.id());
    }

    @Test
    @Order(25)
    void updateStatusWithNote() {
        var exchange = service.create(
                station.id(), member.id(), "Exch Tester", null, inventoryId, null, null, "Note test", null);
        service.updateStatus(exchange.id(), ExchangeStatus.RECEIVED, member.id(), "Shipped note");
        var logs = service.findLogs(exchange.id());
        assertTrue(logs.stream().anyMatch(l -> "Shipped note".equals(l.note())));
        service.delete(exchange.id());
    }
}
