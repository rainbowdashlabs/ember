/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
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
        var inventoryService = new InventoryService(
                inventoryRepo,
                artRepo,
                fieldDefinitionService,
                itemCustodyService,
                clusterRepo,
                clusterStationGroupRepo);
        service = new ProcurementService(
                procurementRepo,
                inventoryService,
                inventoryRepo,
                clusterRepo,
                itemCustodyService,
                itemMovementService,
                stationMemberRepo,
                accountRepo,
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

    /**
     * What arrives against an order is on its way to whoever it was ordered for, not in their hands.
     *
     * <p>An order is a promise: the piece turns up at the station and somebody gives it to the member
     * at the next duty. Writing it straight onto them recorded a hand-over that had not happened, and
     * the shelf then said a piece was gone that was still lying there.
     */
    @Test
    @Order(12)
    void whatArrivesAgainstAnOrderIsOnItsWayToTheMember() {
        var ordered = service.create(station.id(), inventoryId, member.id(), null, "Neue Jacke");

        assertTrue(service.fulfill(ordered.id()));

        var handOut = itemMovementService.findByMember(member.id()).stream()
                .filter(movement -> movement.purpose() == MovementPurpose.ISSUE)
                .filter(movement -> movement.state() == MovementState.OPEN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the order should have set a hand-out going"));

        assertNotNull(handOut.incomingItemId(), "the piece that arrived is the one promised");
        var piece = inventoryRepo.findItemById(handOut.incomingItemId()).orElseThrow();
        assertNull(piece.assignedTo(), "and it is not on the member until somebody hands it over");
        assertEquals(inventoryId, piece.inventoryId(), "it landed in the inventory that was ordered from");

        itemMovementService.abandon(handOut.id(), "Test vorbei");
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

    /**
     * A cluster orders for its own store, so the order names nobody and what arrives belongs to the
     * cluster and rests there, rather than landing on a person its station does not have.
     */
    @Test
    @Order(40)
    void anOrderForNobodyArrivesInTheClustersOwnStore() {
        var home = stationRepo.create("Träger Beschaffung");
        var cluster = clusterRepo.create("Kreisverband Beschaffung", null, home.id());
        var pool = inventoryRepo.create(home.id(), "Einsatzkleidung", InventoryType.EXTERNAL, false);

        var ordered = service.create(home.id(), pool.id(), null, null, "Nachbestellung");
        assertNull(ordered.memberId(), "an order need not be for anybody");

        assertTrue(service.fulfill(ordered.id()));

        var arrived = inventoryRepo.findItemsOwnedByCluster(cluster.id());
        assertEquals(1, arrived.size(), "one piece, belonging to the cluster that ordered it");
        assertEquals(ItemCustody.WITH_OWNER, arrived.getFirst().custody(), "resting in its own store");
        assertNull(arrived.getFirst().assignedTo(), "and on nobody");

        inventoryRepo.deleteItem(arrived.getFirst().id());
        inventoryRepo.delete(pool.id());
        clusterRepo.delete(cluster.id());
        stationRepo.delete(home.id());
    }
}
