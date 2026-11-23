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
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.OldPieceWhereabouts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * Calling off an exchange, and what happens to the piece the member handed in.
 *
 * <p>Each test gets a station of its own, so the pieces and chains of one never meet another's.
 */
class MovementCallOffTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();

    private Station station;
    private Account account;
    private StationMember member;
    private int inventoryId;
    private ItemMovementService.Actor team;

    @BeforeEach
    void setup() {
        int code = CODES.incrementAndGet();
        station = stationRepo.create("CallOffStation " + code);
        account = accountRepo.create("calloff" + code + "@test.com", "Call", "Off");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Jacken", InventoryType.MIXED, false)
                .id();
        team = new ItemMovementService.Actor(member.id(), true);
    }

    @AfterEach
    void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int pieceWithMember(ItemOwner owner) {
        int id = inventoryRepo
                .createItem(inventoryId, "C-" + CODES.incrementAndGet(), "Jacke", null, null, owner, null)
                .id();
        itemCustodyService.assignToMember(id, member.id(), "Call Off");
        return id;
    }

    private ItemMovement exchange(int itemId) {
        return itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Call Off",
                itemId,
                inventoryId,
                null,
                null,
                "Zu klein",
                team,
                null);
    }

    private ItemMovement walk(ItemMovement movement, int steps) {
        for (int step = 0; step < steps; step++) {
            movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        }
        return movement;
    }

    private ItemMovement callOff(ItemMovement movement, boolean giveBack, boolean planWayBack) {
        return itemMovementService.cancel(
                movement.id(), team, "Doch nicht", new ItemMovementService.CallOff(giveBack, planWayBack), "Call Off");
    }

    /**
     * The movement service of a station that binds no chain for handing the association's gear to a
     * member. The presets bind one on every station and put it back when it is taken away, so the gap
     * is made where the chain is looked up.
     */
    private static ItemMovementService serviceWithoutAHandOutChain() {
        MovementFlowService flows = spy(movementFlowService);
        doThrow(InventoryRefusal.MOVEMENT_FLOW_NOT_BOUND.raise())
                .when(flows)
                .resolveFlow(
                        anyInt(),
                        any(),
                        eq(ItemOwner.CLUSTER),
                        any(),
                        eq(MovementPurpose.ISSUE),
                        eq(MovementParty.MEMBER));
        return new ItemMovementService(
                itemMovementRepo,
                flows,
                inventoryRepo,
                itemCustodyService,
                clusterRepo,
                itemMovementItemRepo,
                new MovementTargeting(inventoryRepo, clusterRepo, flows),
                new DomainEventBus(Set.of()));
    }

    private InventoryItem piece(int itemId) {
        return inventoryRepo.findItemById(itemId).orElseThrow();
    }

    /** The case that reached a release: the station's own piece taken back sat on the shelf for good. */
    @Test
    void theStationsOwnPieceOnItsShelfGoesBackToTheMember() {
        int old = pieceWithMember(ItemOwner.STATION);
        ItemMovement handedIn = walk(exchange(old), 1);
        assertEquals(ItemCustody.WITH_OWNER, piece(old).custody(), "taken back onto the station's shelf");
        assertEquals(OldPieceWhereabouts.AT_STATION, itemMovementService.whereTheOldPieceIs(handedIn));

        ItemMovement cancelled = callOff(handedIn, true, false);

        assertEquals(MovementState.CANCELLED, cancelled.state());
        assertEquals(ItemCustody.WITH_MEMBER, piece(old).custody(), "the member holds it again");
        assertEquals(member.id(), piece(old).assignedTo());
    }

    /** A broken piece is kept at the station when the caller says so. */
    @Test
    void theStationsOwnPieceStaysOnTheShelfWhenKept() {
        int old = pieceWithMember(ItemOwner.STATION);
        ItemMovement handedIn = walk(exchange(old), 1);

        callOff(handedIn, false, false);

        assertEquals(ItemCustody.WITH_OWNER, piece(old).custody(), "it stays on the shelf");
        assertNull(piece(old).assignedTo(), "and nobody holds it");
    }

    /** The association's piece handed in at the station but not yet sent is given back on the spot. */
    @Test
    void theAssociationsPieceStillAtTheStationGoesBackToTheMember() {
        int old = pieceWithMember(ItemOwner.CLUSTER);
        ItemMovement handedIn = walk(exchange(old), 1);
        assertEquals(ItemCustody.AT_STATION, piece(old).custody());

        callOff(handedIn, true, false);

        assertEquals(ItemCustody.WITH_MEMBER, piece(old).custody());
        assertEquals(member.id(), piece(old).assignedTo());
    }

    /** A piece already sent stays with its owner when no way back is asked for. */
    @Test
    void aPieceAlreadySentStaysAwayWithoutAWayBack() {
        int old = pieceWithMember(ItemOwner.CLUSTER);
        ItemMovement sent = walk(exchange(old), 2);
        assertEquals(ItemCustody.IN_TRANSIT, piece(old).custody());
        assertEquals(OldPieceWhereabouts.AWAY, itemMovementService.whereTheOldPieceIs(sent));

        callOff(sent, true, false);

        assertEquals(ItemCustody.WITH_OWNER, piece(old).custody(), "it settles with its owner");
        assertFalse(itemMovementService.isOnAMovement(old), "and nothing is planned for it");
    }

    /**
     * A piece already sent is brought back by a hand-out of its own, which names it from the start and
     * does not put it on the member before it is handed over.
     */
    @Test
    void aPieceAlreadySentGetsAWayBackWhenAskedFor() {
        int old = pieceWithMember(ItemOwner.CLUSTER);
        ItemMovement sent = walk(exchange(old), 2);

        ItemMovement cancelled = callOff(sent, true, true);

        assertEquals(MovementState.CANCELLED, cancelled.state());
        ItemMovement wayBack = itemMovementRepo.findOpenByIncomingItem(old).orElseThrow();
        assertEquals(MovementPurpose.ISSUE, wayBack.purpose());
        assertEquals(member.id(), wayBack.memberId());
        assertTrue(piece(old).custody() != ItemCustody.WITH_MEMBER, "nothing claims the member holds it yet");
    }

    /** Without a chain for the way back, the call-off is refused and the movement runs on as it was. */
    @Test
    void aWayBackWithoutAChainIsRefusedAndChangesNothing() {
        int old = pieceWithMember(ItemOwner.CLUSTER);
        ItemMovement sent = walk(exchange(old), 2);
        ItemMovementService withoutAChain = serviceWithoutAHandOutChain();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> withoutAChain.cancel(
                        sent.id(), team, "Doch nicht", new ItemMovementService.CallOff(true, true), "Call Off"));

        assertEquals(InventoryRefusal.MOVEMENT_FLOW_NOT_BOUND, refused.refusal());
        assertEquals(
                MovementState.OPEN,
                itemMovementRepo.findById(sent.id()).orElseThrow().state());
        assertEquals(ItemCustody.IN_TRANSIT, piece(old).custody(), "the piece is where it was");
    }

    /**
     * A way back the chain is bound for but cannot start, here because the chain has no steps left,
     * leaves the exchange running and its piece where it was: closing and starting are one write.
     */
    @Test
    void aWayBackThatCannotStartLeavesTheExchangeRunning() {
        int old = pieceWithMember(ItemOwner.CLUSTER);
        ItemMovement sent = walk(exchange(old), 2);
        int handOutChain = movementFlowRepo
                .findBoundFlow(
                        station.id(), inventoryId, ItemOwner.CLUSTER, MovementPurpose.ISSUE, MovementParty.MEMBER)
                .orElseThrow();
        movementFlowRepo.findActiveSteps(handOutChain).forEach(step -> movementFlowRepo.archiveStep(step.id()));

        var refused = assertThrows(RefusalResponse.class, () -> callOff(sent, true, true));

        assertEquals(InventoryRefusal.MOVEMENT_FLOW_HAS_NO_STEPS, refused.refusal());
        assertEquals(
                MovementState.OPEN,
                itemMovementRepo.findById(sent.id()).orElseThrow().state());
        assertEquals(ItemCustody.IN_TRANSIT, piece(old).custody(), "the piece is where it was");
        assertTrue(itemMovementRepo.findOpenByIncomingItem(old).isEmpty(), "and no way back was left behind");
    }

    /** A member calling off while still holding the piece keeps it, as before. */
    @Test
    void aMemberCallingOffKeepsThePieceTheyHold() {
        int old = pieceWithMember(ItemOwner.STATION);
        ItemMovement asked = exchange(old);
        assertEquals(OldPieceWhereabouts.WITH_MEMBER, itemMovementService.whereTheOldPieceIs(asked));

        ItemMovement cancelled =
                itemMovementService.cancel(asked.id(), new ItemMovementService.Actor(member.id(), false), "Passt doch");

        assertEquals(MovementState.CANCELLED, cancelled.state());
        assertEquals(ItemCustody.WITH_MEMBER, piece(old).custody());
        assertEquals(member.id(), piece(old).assignedTo());
    }
}
