/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.Inventory;
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

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProcurementRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static Inventory inventory;
    private static int procurementId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Procurement Station");
        account = accountRepo.create("procurement@test.com", "Proc", "User");
        member = stationMemberRepo.create(station.id(), account.id());
        inventory = inventoryRepo.create(station.id(), "Proc Inv", InventoryType.EXTERNAL, false);
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
        var proc = procurementRepo.create(station.id(), inventory.id(), member.id(), null, "Need helmet");
        assertNotNull(proc);
        assertEquals(station.id(), proc.stationId());
        assertEquals(member.id(), proc.memberId());
        assertNull(proc.fulfilledAt());
        procurementId = proc.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(procurementRepo.findById(procurementId).isPresent());
        assertTrue(procurementRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        assertEquals(1, procurementRepo.findByStation(station.id()).size());
    }

    @Test
    @Order(4)
    void findOpen() {
        assertEquals(1, procurementRepo.findOpen(station.id()).size());
    }

    @Test
    @Order(5)
    void fulfill() {
        assertTrue(procurementRepo.fulfill(procurementId));
        var proc = procurementRepo.findById(procurementId).orElseThrow();
        assertNotNull(proc.fulfilledAt());
    }

    @Test
    @Order(6)
    void findOpenAfterFulfill() {
        assertEquals(0, procurementRepo.findOpen(station.id()).size());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(procurementRepo.delete(procurementId));
        assertTrue(procurementRepo.findById(procurementId).isEmpty());
    }
}
