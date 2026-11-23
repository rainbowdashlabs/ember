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
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A swap whose replacement is not on the shelf waits for an order, and delivering the order names the
 * replacement on the swap.
 */
class SwapProcurementTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();
    private static ProcurementService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static ItemMovementService.Actor team;

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
        station = stationRepo.create("SwapProcurementStation");
        account = accountRepo.create("swap-procurement@test.com", "Swap", "Order");
        member = stationMemberRepo.create(station.id(), account.id());
        team = new ItemMovementService.Actor(member.id(), true);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /** An inventory of its own per story, so what lies free on one shelf decides nothing on another. */
    private int shelf() {
        return inventoryRepo
                .create(station.id(), "Jacken " + CODES.incrementAndGet(), InventoryType.INTERNAL, false)
                .id();
    }

    private int piece(int inventoryId) {
        return inventoryRepo
                .createItem(inventoryId, "J-" + CODES.incrementAndGet(), "Jacke", null, null)
                .id();
    }

    /** A swap of a jacket the member holds, walked to the step that names its replacement. */
    private ItemMovement swapWaitingForItsReplacement(int inventoryId) {
        int old = piece(inventoryId);
        itemCustodyService.assignToMember(old, member.id(), "Swap Order");
        ItemMovement swap = itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Swap Order",
                old,
                inventoryId,
                null,
                null,
                "Zu klein",
                team,
                null);
        int guard = 10;
        while (guard-- > 0 && !namesTheArrival(swap)) {
            swap = itemMovementService.acknowledge(swap.id(), swap.currentStepId(), team, "", null);
        }
        assertTrue(namesTheArrival(swap), "the swap stands on the step that names its replacement");
        return swap;
    }

    private boolean namesTheArrival(ItemMovement movement) {
        return pickingStep(movement).isPresent();
    }

    private Optional<MovementFlowStep> pickingStep(ItemMovement movement) {
        Integer stepId = movement.currentStepId();
        if (stepId == null) return Optional.empty();
        return itemMovementService.stepsOf(movement).stream()
                .filter(step -> step.id() == stepId)
                .filter(ItemMovementService::namesIncomingItem)
                .findFirst();
    }

    private ItemMovement reread(ItemMovement movement) {
        return itemMovementService.findById(movement.id()).orElseThrow();
    }

    private InventoryRefusal refusalOf(Runnable call) {
        return (InventoryRefusal) assertThrows(RefusalResponse.class, call::run).refusal();
    }

    @Test
    void aSwapWaitsForItsOrderAndDeliveringItNamesTheReplacement() {
        int shelf = shelf();
        ItemMovement swap = swapWaitingForItsReplacement(shelf);

        var order = service.createForSwap(station.id(), swap.id());
        assertEquals(swap.id(), order.movementId());
        assertEquals(member.id(), order.memberId(), "the order is for the member of the swap");
        assertEquals("Zu klein", order.notes(), "and carries the swap's reason");
        assertEquals(Optional.of(order.id()), itemMovementService.openOrderOf(swap.id()));
        assertEquals(Optional.of(swap.id()), service.waitingSwapOf(order).map(ItemMovement::id));

        int turnedUp = piece(shelf);
        int step = pickingStep(swap).orElseThrow().id();
        assertEquals(
                InventoryRefusal.MOVEMENT_WAITS_FOR_AN_ORDER,
                refusalOf(() -> itemMovementService.acknowledge(swap.id(), step, team, "", turnedUp)),
                "a piece is not picked by hand while the order is open");

        assertTrue(service.fulfill(order.id(), team));
        ItemMovement walked = reread(swap);
        assertNotNull(walked.incomingItemId(), "the delivered piece is the swap's replacement");
        assertTrue(walked.incomingItemId() != turnedUp, "and it is the delivered piece, not the one on the shelf");
        assertTrue(!namesTheArrival(walked), "the swap walked on");
        assertNotNull(service.findById(order.id()).orElseThrow().fulfilledAt());
        assertTrue(itemMovementService.openOrderOf(swap.id()).isEmpty());
    }

    @Test
    void nothingIsOrderedWhileAFittingPieceIsFree() {
        int shelf = shelf();
        ItemMovement swap = swapWaitingForItsReplacement(shelf);
        piece(shelf);

        assertEquals(
                InventoryRefusal.MOVEMENT_REPLACEMENT_IN_STOCK,
                refusalOf(() -> service.createForSwap(station.id(), swap.id())));
    }

    @Test
    void onlyAStationsOwnSwapIsOrderedForAndOnlyOnce() {
        int shelf = shelf();
        int old = piece(shelf);
        itemCustodyService.assignToMember(old, member.id(), "Swap Order");
        ItemMovement giveBack = itemMovementService.create(
                station.id(),
                MovementPurpose.RETURN,
                member.id(),
                "Swap Order",
                old,
                shelf,
                null,
                null,
                "Zurück",
                team,
                null);
        assertEquals(
                InventoryRefusal.MOVEMENT_NOT_A_SWAP_TO_ORDER_FOR,
                refusalOf(() -> service.createForSwap(station.id(), giveBack.id())),
                "a return is not a swap");

        ItemMovement swap = swapWaitingForItsReplacement(shelf());
        assertEquals(
                InventoryRefusal.MOVEMENT_NOT_A_SWAP_TO_ORDER_FOR,
                refusalOf(() -> service.createForSwap(station.id() + 100000, swap.id())),
                "another station's swap is not this station's to order for");
        service.createForSwap(station.id(), swap.id());
        assertEquals(
                InventoryRefusal.MOVEMENT_ALREADY_WAITS_FOR_AN_ORDER,
                refusalOf(() -> service.createForSwap(station.id(), swap.id())));
    }

    @Test
    void callingTheSwapOffKeepsItsOrderAsOneOfItsOwn() {
        ItemMovement swap = swapWaitingForItsReplacement(shelf());
        var order = service.createForSwap(station.id(), swap.id());

        itemMovementService.cancel(swap.id(), team, "Passt doch");

        var kept = service.findById(order.id()).orElseThrow();
        assertEquals(MovementState.CANCELLED, reread(swap).state());
        assertNull(kept.movementId(), "the order no longer waits for the swap");
        assertNull(kept.fulfilledAt(), "and is still open");
        assertTrue(service.waitingSwapOf(kept).isEmpty());
    }

    @Test
    void issuingFromStockNamesTheStockPieceOnTheSwap() {
        int shelf = shelf();
        ItemMovement swap = swapWaitingForItsReplacement(shelf);
        var order = service.createForSwap(station.id(), swap.id());
        int fromStock = piece(shelf);

        ItemMovement walked = service.issueFromStock(order.id(), fromStock, team);

        assertEquals(swap.id(), walked.id(), "the swap hands the piece out, no second movement");
        assertEquals(fromStock, walked.incomingItemId());
        assertNotNull(service.findById(order.id()).orElseThrow().fulfilledAt());
    }

    @Test
    void aSwapIsOrderedForOnlyOnTheStepThatNamesItsReplacement() {
        int shelf = shelf();
        int old = piece(shelf);
        itemCustodyService.assignToMember(old, member.id(), "Swap Order");
        ItemMovement fresh = itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Swap Order",
                old,
                shelf,
                null,
                null,
                "Kaputt",
                team,
                null);
        assertTrue(!namesTheArrival(fresh), "a fresh swap first takes the old piece back");

        assertEquals(
                InventoryRefusal.MOVEMENT_NOT_WAITING_FOR_ITS_REPLACEMENT,
                refusalOf(() -> service.createForSwap(station.id(), fresh.id())));
    }

    @Test
    void aSwapOfTheAssociationsGearIsNotTheStationsToOrderFor() {
        int mixed = inventoryRepo
                .create(station.id(), "Gemischt " + CODES.incrementAndGet(), InventoryType.MIXED, false)
                .id();
        int theirs = inventoryRepo
                .createItem(mixed, "C-" + CODES.incrementAndGet(), "Helm", null, null, ItemOwner.CLUSTER, null)
                .id();
        itemCustodyService.assignToMember(theirs, member.id(), "Swap Order");
        ItemMovement swap = itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Swap Order",
                theirs,
                mixed,
                null,
                null,
                "Kaputt",
                team,
                null);

        assertEquals(
                InventoryRefusal.MOVEMENT_NOT_A_SWAP_TO_ORDER_FOR,
                refusalOf(() -> service.createForSwap(station.id(), swap.id())));
    }

    @Test
    void aDeliveryNobodyMayAnswerTheSwapForChangesNothing() {
        int shelf = shelf();
        ItemMovement swap = swapWaitingForItsReplacement(shelf);
        var order = service.createForSwap(station.id(), swap.id());
        int pieces = inventoryRepo.findItems(shelf).size();

        assertEquals(
                InventoryRefusal.MOVEMENT_STEP_NOT_YOUR_TURN,
                refusalOf(() -> service.fulfill(order.id(), new ItemMovementService.Actor(member.id(), false))));

        var still = service.findById(order.id()).orElseThrow();
        assertNull(still.fulfilledAt(), "the order is still open");
        assertEquals(swap.id(), still.movementId(), "and still waited for");
        assertEquals(pieces, inventoryRepo.findItems(shelf).size(), "no piece was written down");
        assertNull(reread(swap).incomingItemId());
    }

    @Test
    void deletingTheOrderLetsAPieceBePickedByHand() {
        int shelf = shelf();
        ItemMovement swap = swapWaitingForItsReplacement(shelf);
        var order = service.createForSwap(station.id(), swap.id());
        assertTrue(service.delete(order.id()));
        int turnedUp = piece(shelf);

        ItemMovement walked = itemMovementService.acknowledge(
                swap.id(), pickingStep(swap).orElseThrow().id(), team, "", turnedUp);

        assertEquals(turnedUp, walked.incomingItemId());
    }

    @Test
    void anOrderWhoseSwapWasCalledOffIsDeliveredAsAnOrderOfItsOwn() {
        ItemMovement swap = swapWaitingForItsReplacement(shelf());
        var order = service.createForSwap(station.id(), swap.id());
        itemMovementService.cancel(swap.id(), team, "Passt doch");

        assertTrue(service.fulfill(order.id(), team));

        assertNotNull(service.findById(order.id()).orElseThrow().fulfilledAt());
        ItemMovement calledOff = reread(swap);
        assertEquals(MovementState.CANCELLED, calledOff.state(), "the called-off swap stays as it ended");
        assertNull(calledOff.incomingItemId(), "and names no replacement");
    }

    @Test
    void aSwapLeavingItsStepOrGettingItsArrivalLetsGoOfTheOrder() {
        int shelf = shelf();
        ItemMovement named = swapWaitingForItsReplacement(shelf);
        var forNamed = service.createForSwap(station.id(), named.id());
        itemMovementRepo.setIncomingItem(named.id(), piece(shelf));
        assertNull(service.findById(forNamed.id()).orElseThrow().movementId(), "a named arrival lets go");

        ItemMovement moved = swapWaitingForItsReplacement(shelf());
        var forMoved = service.createForSwap(station.id(), moved.id());
        int earlier = itemMovementService.stepsOf(moved).getFirst().id();
        itemMovementRepo.moveToStep(moved.id(), earlier);
        assertNull(service.findById(forMoved.id()).orElseThrow().movementId(), "another step lets go");
        assertTrue(itemMovementService.openOrderOf(moved.id()).isEmpty());
    }

    @Test
    void aSwapKeepingTheSizeOrdersTheSizeItGivesBack() {
        int sized = inventoryRepo
                .create(station.id(), "Stiefel " + CODES.incrementAndGet(), InventoryType.INTERNAL, true)
                .id();
        inventoryRepo.createSize(sized, "42", 0, null);
        int size42 = inventoryRepo.findSizes(sized).getFirst().id();
        int old = inventoryRepo
                .createItem(sized, "S-" + CODES.incrementAndGet(), "Stiefel", size42, null)
                .id();
        itemCustodyService.assignToMember(old, member.id(), "Swap Order");
        piece(sized);
        ItemMovement swap = itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Swap Order",
                old,
                sized,
                size42,
                null,
                "Kaputt",
                team,
                null);
        int guard = 10;
        while (guard-- > 0 && !namesTheArrival(swap)) {
            swap = itemMovementService.acknowledge(swap.id(), swap.currentStepId(), team, "", null);
        }

        var order = service.createForSwap(station.id(), swap.id());

        assertEquals(size42, order.sizeId(), "a piece without a size on the shelf does not fit a 42");
    }
}
