/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExchangeRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static Inventory inventory;
    private static int itemId;
    private static int requestId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Exchange Station");
        account = accountRepo.create("exchange@test.com", "Ex", "User");
        member = stationMemberRepo.create(station.id(), account.id());
        inventory = inventoryRepo.create(station.id(), "Ex Inventory", InventoryType.EXTERNAL, true);
        inventoryRepo.createSize(inventory.id(), "M", 1, null);
        int sizeId = inventoryRepo.findSizes(inventory.id()).getFirst().id();
        var item = inventoryRepo.createItem(inventory.id(), "EX-001", "Item", sizeId, "{}");
        itemId = item.id();
    }

    @AfterAll
    static void cleanup() {
        inventoryRepo.delete(inventory.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var request = exchangeRepo.create(
                station.id(), member.id(), itemId, inventory.id(), null, null, "Need larger", member.id());
        assertNotNull(request);
        assertEquals(station.id(), request.stationId());
        assertEquals(member.id(), request.memberId());
        requestId = request.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(exchangeRepo.findById(requestId).isPresent());
        assertTrue(exchangeRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        var requests = exchangeRepo.findByStation(station.id());
        assertEquals(1, requests.size());
    }

    @Test
    @Order(4)
    void findByMember() {
        var requests = exchangeRepo.findByMember(member.id());
        assertEquals(1, requests.size());
    }

    @Test
    @Order(4)
    void countPendingByStation() {
        assertEquals(1, exchangeRepo.countPendingByStation(station.id()));
        assertEquals(0, exchangeRepo.countPendingByStation(-1));
    }

    @Test
    @Order(5)
    void updateStatus() {
        assertTrue(exchangeRepo.updateStatus(requestId, ExchangeStatus.RECEIVED));
        var request = exchangeRepo.findById(requestId).orElseThrow();
        assertEquals(ExchangeStatus.RECEIVED, request.status());
    }

    @Test
    @Order(6)
    void updateStatusWithExchangedItem() {
        assertTrue(exchangeRepo.updateStatusWithExchangedItem(requestId, ExchangeStatus.EXCHANGED, itemId));
        var request = exchangeRepo.findById(requestId).orElseThrow();
        assertEquals(ExchangeStatus.EXCHANGED, request.status());
        assertEquals(0, exchangeRepo.countPendingByStation(station.id()));
    }

    @Test
    @Order(10)
    void createLog() {
        var log = exchangeRepo.createLog(
                requestId, ExchangeStatus.ANNOUNCED, ExchangeStatus.RECEIVED, member.id(), "Received item");
        assertNotNull(log);
        assertEquals(requestId, log.requestId());
        assertEquals(ExchangeStatus.ANNOUNCED, log.oldStatus());
        assertEquals(ExchangeStatus.RECEIVED, log.newStatus());
    }

    @Test
    @Order(11)
    void findLogs() {
        var logs = exchangeRepo.findLogs(requestId);
        assertEquals(1, logs.size());
    }

    @Test
    @Order(12)
    void findLogsEmpty() {
        assertTrue(exchangeRepo.findLogs(99999).isEmpty());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(exchangeRepo.delete(requestId));
        assertTrue(exchangeRepo.findById(requestId).isEmpty());
    }
}
