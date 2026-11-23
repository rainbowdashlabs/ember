/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementCancelled;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.FormerMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Movements are closed, never deleted, once their pieces have moved, and closing settles every piece
 * they carry, the further pieces of a dispatch included.
 */
class MovementClosingTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();

    private Station station;
    private Account account;
    private StationMember member;
    private int inventoryId;
    private ItemMovementService.Actor team;

    @BeforeEach
    void setup() {
        int code = CODES.incrementAndGet();
        station = stationRepo.create("ClosingStation " + code);
        account = accountRepo.create("closing" + code + "@test.com", "Clo", "Sing");
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

    private int piece(ItemOwner owner) {
        return inventoryRepo
                .createItem(inventoryId, "K-" + CODES.incrementAndGet(), "Jacke", null, null, owner, null)
                .id();
    }

    private int pieceWithMember(ItemOwner owner) {
        int id = piece(owner);
        itemCustodyService.assignToMember(id, member.id(), "Clo Sing");
        return id;
    }

    private ItemMovement exchange(int itemId) {
        return itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Clo Sing",
                itemId,
                inventoryId,
                null,
                null,
                "Kaputt",
                team,
                null);
    }

    private ItemMovement walk(ItemMovement movement, int steps) {
        for (int step = 0; step < steps; step++) {
            movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        }
        return movement;
    }

    private ItemCustody custodyOf(int itemId) {
        return inventoryRepo.findItemById(itemId).orElseThrow().custody();
    }

    /** A swap nobody has walked yet may be removed; once a piece has moved it is called off instead. */
    @Test
    void aMovementIsDeletedOnlyWhileNothingHasMoved() {
        ItemMovement untouched = exchange(pieceWithMember(ItemOwner.STATION));
        assertFalse(itemMovementService.piecesMayHaveMoved(untouched.id()));
        assertTrue(itemMovementService.delete(untouched.id()));

        ItemMovement walked = walk(exchange(pieceWithMember(ItemOwner.STATION)), 1);
        assertTrue(itemMovementService.piecesMayHaveMoved(walked.id()));
        var refused = assertThrows(RefusalResponse.class, () -> itemMovementService.delete(walked.id()));
        assertEquals(InventoryRefusal.MOVEMENT_ALREADY_MOVED_ITS_PIECES, refused.refusal());

        itemMovementService.cancel(walked.id(), team, "Doch nicht");
        assertDoesNotThrow(() -> itemMovementService.delete(walked.id()), "a closed one may still go");
    }

    /**
     * A member leaving has their open movements closed rather than deleted, and nothing is put back on
     * them: the piece they handed in stays on the shelf.
     */
    @Test
    void aLeavingMembersMovementsAreClosedNotDeleted() {
        int handedIn = pieceWithMember(ItemOwner.STATION);
        ItemMovement open = walk(exchange(handedIn), 1);
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(handedIn));
        var former = new FormerMemberService(
                stationMemberRepo,
                accountRepo,
                inventoryRepo,
                itemMovementService,
                memberGroupRepo,
                userTagRepo,
                attendanceRepo,
                profileFieldCore,
                mock(DocumentService.class),
                selfCheckService,
                new DomainEventBus(Set.of()));

        former.markFormer(member.id());

        var closed = itemMovementRepo.findById(open.id()).orElseThrow();
        assertEquals(MovementState.CANCELLED, closed.state(), "the record stays");
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(handedIn), "the piece stays on the shelf");
        assertNull(inventoryRepo.findItemById(handedIn).orElseThrow().assignedTo());
    }

    /** Closing a leaving member's movement is announced as called off, with the reason it ended. */
    @Test
    void aLeavingMembersMovementIsAnnouncedAsCalledOff() {
        List<MovementCancelled> told = new ArrayList<>();
        var listening = new ItemMovementService(
                itemMovementRepo,
                movementFlowService,
                inventoryRepo,
                itemCustodyService,
                clusterRepo,
                itemMovementItemRepo,
                movementTargeting,
                new DomainEventBus(Set.of(new DomainEventHandler<MovementCancelled>() {
                    @Override
                    public Class<MovementCancelled> eventType() {
                        return MovementCancelled.class;
                    }

                    @Override
                    public void handle(MovementCancelled event) {
                        told.add(event);
                    }
                })));
        ItemMovement open = exchange(pieceWithMember(ItemOwner.STATION));

        assertEquals(1, listening.closeForALeavingMember(member.id(), "Mitglied ist ausgeschieden"));

        assertEquals(1, told.size());
        assertEquals(open.id(), told.getFirst().movementId());
        assertEquals("Mitglied ist ausgeschieden", told.getFirst().reason());
        assertNull(told.getFirst().memberId(), "the leaving member is not among those told");
    }

    /** Asking everything back passes over a piece a dispatch carries as a further piece. */
    @Test
    void askingEverythingBackPassesOverAFurtherPieceOfAnotherMovement() {
        int carried = pieceWithMember(ItemOwner.STATION);
        int plain = pieceWithMember(ItemOwner.STATION);
        ItemMovement other = exchange(pieceWithMember(ItemOwner.STATION));
        itemMovementItemRepo.add(other.id(), StepSubject.OUTGOING, List.of(carried));

        var started = itemMovementService.requestEverythingBack(station.id(), member.id(), "Clo Sing", team);

        var asked = started.stream().map(ItemMovement::outgoingItemId).toList();
        assertTrue(asked.contains(plain));
        assertFalse(asked.contains(carried), "the carried piece is not asked back twice");
    }

    /** Calling off after the post leaves no further piece in the post on a closed movement. */
    @Test
    void callingOffSettlesEveryPieceInThePost() {
        int named = pieceWithMember(ItemOwner.CLUSTER);
        int extra = pieceWithMember(ItemOwner.CLUSTER);
        ItemMovement swap = exchange(named);
        itemMovementItemRepo.add(swap.id(), StepSubject.OUTGOING, List.of(extra));
        walk(swap, 2);
        assertEquals(ItemCustody.IN_TRANSIT, custodyOf(named));
        assertEquals(ItemCustody.IN_TRANSIT, custodyOf(extra), "the further piece went into the post with it");

        itemMovementService.cancel(swap.id(), team, "Doch nicht");

        assertEquals(ItemCustody.WITH_OWNER, custodyOf(named));
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(extra), "and settles with it");
    }

    /** A correction moves every piece on the side it names. */
    @Test
    void aCorrectionMovesEveryPieceOnItsSide() {
        int named = pieceWithMember(ItemOwner.CLUSTER);
        int extra = pieceWithMember(ItemOwner.CLUSTER);
        ItemMovement swap = exchange(named);
        itemMovementItemRepo.add(swap.id(), StepSubject.OUTGOING, List.of(extra));

        itemMovementService.correct(
                swap.id(),
                new ItemMovementService.Correction(ItemCustody.AT_STATION, null, false, null),
                team,
                "Beide abgegeben");

        assertEquals(ItemCustody.AT_STATION, custodyOf(named));
        assertEquals(ItemCustody.AT_STATION, custodyOf(extra));
    }

    /** A dispatch cannot carry a piece another movement already carries. */
    @Test
    void aFurtherPieceAlreadyCarriedCannotBeSentAgain() {
        ItemMovement first = exchange(pieceWithMember(ItemOwner.STATION));
        int extra = piece(ItemOwner.STATION);
        itemMovementItemRepo.add(first.id(), StepSubject.INCOMING, List.of(extra));

        var refused = assertThrows(
                RefusalResponse.class,
                () -> itemMovementService.create(
                        station.id(),
                        MovementPurpose.ISSUE,
                        member.id(),
                        "Clo Sing",
                        null,
                        inventoryId,
                        null,
                        null,
                        "Zweite Lieferung",
                        team,
                        piece(ItemOwner.STATION),
                        false,
                        List.of(extra)));
        assertEquals(InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT, refused.refusal());
    }
}
