/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
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
class ProcurementServiceTest extends RepositoryTestBase {
    private static ProcurementService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static int procurementId;

    @BeforeAll
    static void setup() {
        var inventoryService = new InventoryService(inventoryRepo, itemCustodyService, clusterRepo);
        service =
                new ProcurementService(
                procurementRepo, inventoryService, inventoryRepo, clusterRepo, itemCustodyService,
                new DomainEventBus(Set.of()));
        station = stationRepo.create("ProcStation");
        account = accountRepo.create("proc-svc@test.com", "Proc", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
        var inv = inventoryRepo.create(station.id(), "Jackets", InventoryType.INTERNAL, false);
        inventoryId = inv.id();
    }

    @AfterAll
    static void cleanup() {
        inventoryRepo.delete(inventoryId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var proc = service.create(station.id(), inventoryId, member.id(), null, "Need a new jacket");
        assertNotNull(proc);
        assertEquals(member.id(), proc.memberId());
        assertNull(proc.fulfilledAt());
        procurementId = proc.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(procurementId).isPresent());
    }

    @Test
    @Order(3)
    void findOpen() {
        var open = service.findOpen(station.id());
        assertTrue(open.stream().anyMatch(p -> p.id() == procurementId));
    }

    @Test
    @Order(10)
    void fulfill() {
        assertTrue(service.fulfill(procurementId));
        var proc = service.findById(procurementId).orElseThrow();
        assertNotNull(proc.fulfilledAt());
    }

    @Test
    @Order(11)
    void fulfilledNotInOpen() {
        var open = service.findOpen(station.id());
        assertFalse(open.stream().anyMatch(p -> p.id() == procurementId));
    }

    @Test
    @Order(20)
    void cancel() {
        var proc2 = service.create(station.id(), inventoryId, member.id(), null, "Cancel me");
        assertTrue(service.delete(proc2.id()));
        assertTrue(service.findById(proc2.id()).isEmpty());
    }

    @Test
    @Order(30)
    void findByStation() {
        assertFalse(service.findByStation(station.id()).isEmpty());
    }

    @Test
    @Order(31)
    void fulfillMissing() {
        assertFalse(service.fulfill(999999));
    }

    @Test
    @Order(32)
    void deleteMissing() {
        assertFalse(service.delete(999999));
    }
}
