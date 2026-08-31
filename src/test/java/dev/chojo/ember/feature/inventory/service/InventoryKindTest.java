/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.SwitchBlockerKind;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an inventory holds: one thing in many copies, or a drawer of different things.
 *
 * <p>Three features only mean something for the first, so they are refused on the second; the switch
 * between the two is refused while anything live still depends on the kind being left; and moving a
 * piece from one inventory to another is what makes that barrier honest rather than a trap.
 */
class InventoryKindTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static InventoryService service;
    private static ProcurementService procurementService;
    private static ExchangeService exchangeService;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new InventoryService(inventoryRepo, itemCustodyService, clusterRepo, clusterStationGroupRepo);
        procurementService = new ProcurementService(
                procurementRepo, service, inventoryRepo, clusterRepo, itemCustodyService, new DomainEventBus(Set.of()));
        exchangeService = new ExchangeService(itemMovementService, inventoryRepo);
        station = stationRepo.create("KindStation");
        account = accountRepo.create("kind-svc@test.com", "Kind", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int drawer(String name) {
        return service.create(station.id(), name + NAMES.incrementAndGet(), InventoryType.INTERNAL, false, false)
                .id();
    }

    private static int oneThing(String name, boolean hasSizes) {
        return service.create(station.id(), name + NAMES.incrementAndGet(), InventoryType.INTERNAL, hasSizes, true)
                .id();
    }

    // -- The barriers --

    @Test
    void aRequirementIsRefusedOnADrawerOfDifferentThings() {
        int id = drawer("Sonstiges");
        assertThrows(BadRequestResponse.class, () -> service.createRequirement(id, StationUserType.MEMBER, 0, null, 1));
        assertTrue(service.findAllRequirementsByStation(station.id()).stream()
                .noneMatch(requirement -> requirement.inventoryId() == id));
        service.delete(id);
    }

    @Test
    void anOrderIsRefusedOnADrawerOfDifferentThings() {
        int id = drawer("Spiele");
        assertThrows(
                BadRequestResponse.class,
                () -> procurementService.create(station.id(), id, member.id(), null, "Drei mehr wovon?"));
        service.delete(id);
    }

    @Test
    void anExchangeIsRefusedOnADrawerOfDifferentThings() {
        int id = drawer("Leibchen");
        var item = inventoryRepo.createItem(id, "LB-001", "Laminiergerät", null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        assertThrows(
                BadRequestResponse.class,
                () -> exchangeService.create(
                        station.id(), member.id(), "Kind Tester", item.id(), id, null, null, "Zu klein", null));
        service.delete(id);
    }

    @Test
    void aSizeListIsRefusedOnADrawerOfDifferentThings() {
        int id = drawer("Pager");
        assertThrows(BadRequestResponse.class, () -> service.createSize(id, "M", 0, null));
        assertTrue(service.findSizes(id).isEmpty());
        service.delete(id);
    }

    /** Asking for sizes on a drawer is not an error to report but a combination that cannot exist. */
    @Test
    void aDrawerOfDifferentThingsIsCreatedWithoutSizes() {
        var inventory =
                service.create(station.id(), "Kiste" + NAMES.incrementAndGet(), InventoryType.INTERNAL, true, false);
        assertFalse(inventory.hasSizes());
        assertFalse(inventory.homogeneous());
        service.delete(inventory.id());
    }

    // -- The switch --

    @Test
    void aRequirementStandsInTheWayOfBecomingADrawer() {
        int id = oneThing("Helm", false);
        var requirement = service.createRequirement(id, StationUserType.MEMBER, 0, null, 1);

        var refused = assertThrows(
                InventorySwitchRefusedException.class,
                () -> service.update(id, "Helm", InventoryType.INTERNAL, false, false));
        assertTrue(refused.blockers().stream()
                .anyMatch(blocker ->
                        blocker.kind() == SwitchBlockerKind.REQUIREMENT && blocker.id() == requirement.id()));

        // and it goes through once nothing asks for it any more
        service.deleteRequirement(requirement.id());
        var switched = service.update(id, "Helm", InventoryType.INTERNAL, false, false);
        assertTrue(switched.isPresent());
        assertFalse(switched.get().homogeneous());
        service.delete(id);
    }

    @Test
    void aSizeListStandsInTheWayOfBecomingADrawer() {
        int id = oneThing("Stiefel", true);
        service.createSize(id, "42", 0, null);
        int sizeId = service.findSizes(id).getFirst().id();

        var refused = assertThrows(
                InventorySwitchRefusedException.class,
                () -> service.update(id, "Stiefel", InventoryType.INTERNAL, true, false));
        assertTrue(refused.blockers().stream()
                .anyMatch(blocker -> blocker.kind() == SwitchBlockerKind.SIZE && blocker.id() == sizeId));

        service.deleteSize(id, sizeId);
        assertTrue(service.update(id, "Stiefel", InventoryType.INTERNAL, true, false)
                .isPresent());
        service.delete(id);
    }

    /** Putting the size list away would strand the sizes the pieces are carrying, so it waits too. */
    @Test
    void theSizeListCannotBePutAwayWhileItHasSizesOnIt() {
        int id = oneThing("T-Shirt", true);
        service.createSize(id, "152", 0, null);
        int sizeId = service.findSizes(id).getFirst().id();

        var refused = assertThrows(
                InventorySwitchRefusedException.class,
                () -> service.update(id, "T-Shirt", InventoryType.INTERNAL, false, true));
        assertTrue(refused.blockers().stream()
                .anyMatch(blocker -> blocker.kind() == SwitchBlockerKind.SIZE && blocker.id() == sizeId));

        service.deleteSize(id, sizeId);
        var switched = service.update(id, "T-Shirt", InventoryType.INTERNAL, false, true);
        assertTrue(switched.isPresent());
        assertFalse(switched.get().hasSizes());
        service.delete(id);
    }

    /**
     * An order that has been fulfilled is history, and history must never make an inventory
     * permanently unswitchable.
     */
    @Test
    void onlyAnUnfulfilledOrderStandsInTheWay() {
        int id = oneThing("Handschuhe", false);
        var order = procurementService.create(station.id(), id, member.id(), null, "Drei mehr");

        var refused = assertThrows(
                InventorySwitchRefusedException.class,
                () -> service.update(id, "Handschuhe", InventoryType.INTERNAL, false, false));
        assertTrue(refused.blockers().stream()
                .anyMatch(blocker -> blocker.kind() == SwitchBlockerKind.PROCUREMENT && blocker.id() == order.id()));

        assertTrue(procurementService.fulfill(order.id()));
        var switched = service.update(id, "Handschuhe", InventoryType.INTERNAL, false, false);
        assertTrue(switched.isPresent());
        assertFalse(switched.get().homogeneous());
        service.delete(id);
    }

    /** The same for an exchange: one that has stopped moving does not hold anything in place. */
    @Test
    void onlyAnOpenExchangeStandsInTheWay() {
        int id = oneThing("Parka", false);
        var item = inventoryRepo.createItem(id, "PA-" + NAMES.incrementAndGet(), "Parka", null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        var exchange = exchangeService.create(
                station.id(), member.id(), "Kind Tester", item.id(), id, null, null, "Zu klein", null);

        var refused = assertThrows(
                InventorySwitchRefusedException.class,
                () -> service.update(id, "Parka", InventoryType.INTERNAL, false, false));
        assertTrue(refused.blockers().stream()
                .anyMatch(blocker -> blocker.kind() == SwitchBlockerKind.EXCHANGE && blocker.id() == exchange.id()));

        itemMovementService.abandon(exchange.id(), "Doch nicht");
        assertTrue(
                itemMovementRepo.findById(exchange.id()).orElseThrow().state().closed());

        var switched = service.update(id, "Parka", InventoryType.INTERNAL, false, false);
        assertTrue(switched.isPresent());
        assertFalse(switched.get().homogeneous());
        service.delete(id);
    }

    /** Coming back the other way is open, because the Arten that will block it do not exist yet. */
    @Test
    void becomingOneThingAgainIsNotBlockedByAnything() {
        int id = drawer("Zurück");
        var switched = service.update(id, "Zurück", InventoryType.INTERNAL, false, true);
        assertTrue(switched.isPresent());
        assertTrue(switched.get().homogeneous());
        assertTrue(service.blockersForSwitch(switched.get(), true).isEmpty());
        service.delete(id);
    }

    /** Renaming an inventory says nothing about what it holds, so it must not quietly change it. */
    @Test
    void aRenameLeavesTheKindAlone() {
        int id = drawer("Umbenannt");
        var renamed = service.update(id, "Immer noch eine Kiste", InventoryType.INTERNAL, false, false);
        assertTrue(renamed.isPresent());
        assertFalse(renamed.get().homogeneous());
        service.delete(id);
    }

    // -- The move --

    @Test
    void movingAPieceKeepsItsIdentityAndItsHistory() {
        int from = oneThing("Bundhose leicht", false);
        int to = oneThing("Bundhose schwer", false);
        var item = inventoryRepo.createItem(from, "BH-" + NAMES.incrementAndGet(), "Bundhose", null, null);
        service.assignItem(item.id(), member.id(), "Kind Tester");
        service.assignItem(item.id(), null, "Kind Tester");
        int historyBefore = service.findHistory(item.id()).size();
        assertTrue(historyBefore > 0);

        var moved = service.moveItem(item.id(), to, null).orElseThrow();
        assertEquals(item.id(), moved.id());
        assertEquals(to, moved.inventoryId());
        assertEquals(item.internalId(), moved.internalId());
        assertEquals(historyBefore, service.findHistory(item.id()).size());

        service.delete(from);
        service.delete(to);
    }

    /** The size list belongs to the inventory being left, so a size of the same name is found again. */
    @Test
    void movingAPieceRemapsASizeOfTheSameName() {
        int from = oneThing("Blouson alt", true);
        int to = oneThing("Blouson neu", true);
        service.createSize(from, "152", 0, null);
        service.createSize(to, "152", 0, null);
        int fromSize = service.findSizes(from).getFirst().id();
        int toSize = service.findSizes(to).getFirst().id();
        var item = inventoryRepo.createItem(from, "BL-" + NAMES.incrementAndGet(), "Blouson", fromSize, null);

        var moved = service.moveItem(item.id(), to, null).orElseThrow();
        assertNotNull(moved.sizeId());
        assertEquals(toSize, moved.sizeId());

        service.delete(from);
        service.delete(to);
    }

    /** Where there is no size of that name, the piece arrives without one rather than pointing at nothing. */
    @Test
    void movingAPieceClearsASizeTheNewInventoryDoesNotOffer() {
        int from = oneThing("Latzhose", true);
        int to = oneThing("Kappe", false);
        service.createSize(from, "170", 0, null);
        int fromSize = service.findSizes(from).getFirst().id();
        var item = inventoryRepo.createItem(from, "LH-" + NAMES.incrementAndGet(), "Latzhose", fromSize, null);

        var moved = service.moveItem(item.id(), to, null).orElseThrow();
        assertNull(moved.sizeId());

        service.delete(from);
        service.delete(to);
    }

    @Test
    void aPieceCannotBeMovedIntoAnotherStationsInventory() {
        var other = stationRepo.create("KindOtherStation" + NAMES.incrementAndGet());
        int from = oneThing("Sporttasche", false);
        int elsewhere = inventoryRepo
                .create(other.id(), "Fremd", InventoryType.INTERNAL, false)
                .id();
        var item = inventoryRepo.createItem(from, "ST-" + NAMES.incrementAndGet(), "Sporttasche", null, null);

        assertThrows(BadRequestResponse.class, () -> service.moveItem(item.id(), elsewhere, null));
        assertEquals(from, inventoryRepo.findItemById(item.id()).orElseThrow().inventoryId());

        service.delete(from);
        stationRepo.delete(other.id());
    }
}
