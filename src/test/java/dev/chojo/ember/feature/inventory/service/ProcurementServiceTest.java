/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
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
    @Order(1)
    void anOrderForAnInventoryThatIsNotThereIsRefused() {
        var refused = assertThrows(
                RefusalResponse.class, () -> service.create(station.id(), Integer.MAX_VALUE, member.id(), null, ""));

        assertEquals(InventoryRefusal.INVENTORY_NOT_HERE_ON_PROCUREMENT, refused.refusal());
    }

    /**
     * An order is placed against an inventory of the station placing it. Naming another station's
     * inventory would put an order, and on fulfilment a new item, into a store the caller does not
     * run, so it is answered as though the inventory were not there.
     */
    @Test
    @Order(1)
    void anOrderForAnotherStationsInventoryIsRefused() {
        var other = stationRepo.create("Procurement Other " + System.nanoTime());
        var foreign = inventoryRepo.create(other.id(), "Boots", InventoryType.INTERNAL, false);
        try {
            var refused = assertThrows(
                    RefusalResponse.class, () -> service.create(station.id(), foreign.id(), member.id(), null, ""));

            assertEquals(InventoryRefusal.INVENTORY_NOT_HERE_ON_PROCUREMENT, refused.refusal());
            assertTrue(service.findByStation(other.id()).isEmpty());
        } finally {
            inventoryRepo.delete(foreign.id());
            stationRepo.delete(other.id());
        }
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
     * A piece already on the shelf answers an order only in the size that was ordered and only while
     * nothing else has promised it. Settling the order with it hands that piece out and writes nothing
     * new down, since nothing arrived.
     */
    @Test
    @Order(50)
    void anOrderIsSettledWithAMatchingPieceFromStorage() {
        var sized = inventoryRepo.create(station.id(), "Stiefel", InventoryType.INTERNAL, true);
        inventoryRepo.createSize(sized.id(), "42", 0, null);
        inventoryRepo.createSize(sized.id(), "44", 1, null);
        var sizes = inventoryRepo.findSizes(sized.id());
        int ordered = sizes.getFirst().id();
        int other = sizes.getLast().id();
        var fits = inventoryRepo.createItem(sized.id(), "S-1", "Stiefel 42", ordered, null);
        inventoryRepo.createItem(sized.id(), "S-2", "Stiefel 44", other, null);
        var order = service.create(station.id(), sized.id(), member.id(), ordered, "");

        var stock = service.stockFor(order);
        assertEquals(1, stock.size(), "only the piece in the ordered size is offered");
        assertEquals(fits.id(), stock.getFirst().id());

        var handOut = service.issueFromStock(order.id(), fits.id(), new ItemMovementService.Actor(member.id(), true));

        assertEquals(MovementPurpose.ISSUE, handOut.purpose());
        assertNotNull(service.findById(order.id()).orElseThrow().fulfilledAt(), "the order is settled");
        assertEquals(2, inventoryRepo.findItems(sized.id()).size(), "and no new piece was written down");
        var promised = inventoryRepo.findItemById(fits.id()).orElseThrow();
        assertTrue(itemMovementService.isOnAMovement(promised.id()), "the piece is on its way to the member");

        var second = service.create(station.id(), sized.id(), member.id(), ordered, "");
        assertTrue(service.stockFor(second).isEmpty(), "a promised piece is not offered again");
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.issueFromStock(second.id(), fits.id(), new ItemMovementService.Actor(member.id(), true)));
        assertEquals(InventoryRefusal.PROCUREMENT_PIECE_NOT_IN_STOCK, refused.refusal());

        itemMovementService.abandon(handOut.id(), "Test vorbei");
        service.delete(second.id());
    }

    /** An order for nobody has nobody to hand a piece to, so nothing in storage is offered for it. */
    @Test
    @Order(51)
    void anOrderForNobodyIsNotSettledFromStorage() {
        var order = service.create(station.id(), inventoryId, null, null, "");
        inventoryRepo.createItem(inventoryId, "", "Jacke", null, null);

        assertTrue(service.stockFor(order).isEmpty());
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.issueFromStock(order.id(), 1, new ItemMovementService.Actor(member.id(), true)));
        assertEquals(InventoryRefusal.PROCUREMENT_FOR_NOBODY_FROM_STOCK, refused.refusal());

        service.delete(order.id());
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
