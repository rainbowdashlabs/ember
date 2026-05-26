/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
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

import java.util.Set;

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
        var inventoryService = new InventoryService(inventoryRepo);
        service = new ExchangeService(exchangeRepo, inventoryRepo, inventoryService, new DomainEventBus(Set.of()));
        station = stationRepo.create("ExchStation");
        account = accountRepo.create("exch-svc@test.com", "Exch", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
        var inv = inventoryRepo.create(station.id(), "Blouson", InventoryType.INTERNAL, true);
        inventoryId = inv.id();
        inventoryRepo.createSize(inv.id(), "M", 0, null);
        var sizes = inventoryRepo.findSizes(inv.id());
        var sizeM =
                sizes.stream().filter(s -> "M".equals(s.label())).findFirst().orElseThrow();
        var item = inventoryRepo.createItem(inv.id(), "B-001", "Blouson M", sizeM.id(), "{}");
        itemId = item.id();
        inventoryRepo.assignItem(item.id(), member.id());
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
    void updateStatus() {
        var updated = service.updateStatus(exchangeId, ExchangeStatus.SHIPPED, member.id(), null);
        assertNotNull(updated);
        assertEquals(ExchangeStatus.SHIPPED, updated.status());
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
        var newItem = inventoryRepo.createItem(inventoryId, "B-002", "Blouson L", sizeL.id(), "{}");

        var updated =
                service.updateStatus(exchangeId, ExchangeStatus.EXCHANGED, member.id(), "Completed", newItem.id());
        assertNotNull(updated);
        assertEquals(ExchangeStatus.EXCHANGED, updated.status());
    }

    @Test
    @Order(20)
    void cancel() {
        // Create a new exchange for delete testing since the previous one was EXCHANGED
        var sizes = inventoryRepo.findSizes(inventoryId);
        var sizeM =
                sizes.stream().filter(s -> "M".equals(s.label())).findFirst().orElseThrow();
        var exchange = service.create(
                station.id(), member.id(), "Exch Tester", null, inventoryId, sizeM.id(), null, "Delete test", null);
        assertTrue(service.delete(exchange.id()));
        assertTrue(service.findById(exchange.id()).isEmpty());
    }

    @Test
    @Order(25)
    void updateStatusWithNote() {
        var exchange = service.create(
                station.id(), member.id(), "Exch Tester", null, inventoryId, null, null, "Note test", null);
        var updated = service.updateStatus(exchange.id(), ExchangeStatus.SHIPPED, member.id(), "Shipped note");
        assertEquals(ExchangeStatus.SHIPPED, updated.status());
        var logs = service.findLogs(exchange.id());
        assertTrue(logs.stream().anyMatch(l -> "Shipped note".equals(l.note())));
        service.delete(exchange.id());
    }
}
