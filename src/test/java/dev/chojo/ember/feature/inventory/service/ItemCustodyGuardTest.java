/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService.Allowance;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The one question every custody write asks first: whether a piece is free to act on.
 */
class ItemCustodyGuardTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();
    private static final LendingRepository LENDING = new LendingRepository();

    private static Station station;
    private static Station partner;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static ItemMovementService.Actor team;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("GuardStation");
        partner = stationRepo.create("GuardPartner");
        account = accountRepo.create("guard@test.com", "Gu", "Ard");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Funkgeräte", InventoryType.MIXED, false)
                .id();
        team = new ItemMovementService.Actor(member.id(), true);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(partner.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int piece() {
        return inventoryRepo
                .createItem(
                        inventoryId, "G-" + CODES.incrementAndGet(), "Funkgerät", null, null, ItemOwner.STATION, null)
                .id();
    }

    private int pieceWithMember() {
        int id = piece();
        itemCustodyService.assignToMember(id, member.id(), "Gu Ard");
        return id;
    }

    private ItemMovement exchange(int itemId) {
        return itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Gu Ard",
                itemId,
                inventoryId,
                null,
                null,
                "Kaputt",
                team,
                null);
    }

    private ItemMovement plannedHandOut(int itemId) {
        return itemMovementService.create(
                station.id(),
                MovementPurpose.ISSUE,
                member.id(),
                "Gu Ard",
                null,
                inventoryId,
                null,
                null,
                "Für den Dienst",
                team,
                itemId);
    }

    /** Sets the piece aside on a loan to the partner, in the given state and ending on the given day. */
    private void setAsideForALoan(int itemId, LendingStatus status, LocalDate until) {
        var request = LENDING.createRequest(
                partner.uid(), station.uid(), LocalDate.now().minusDays(1), until, member.id(), null, null, "");
        var line = LENDING.addRequestItem(request.id(), inventoryId, null, null, 1, null);
        LENDING.assignItem(line.id(), itemId);
        LENDING.updateRequestStatus(request.id(), status);
    }

    private static void refusedWith(InventoryRefusal expected, Executable write) {
        assertEquals(expected, assertThrows(RefusalResponse.class, write).refusal());
    }

    @Test
    void aFreePieceIsFreeToActOn() {
        int free = piece();

        assertDoesNotThrow(() -> itemCustodyService.requireFreeToActOn(free, Allowance.NONE));
    }

    /** A loss is left alone by everything but the loss itself and its finding. */
    @Test
    void aMissingPieceIsFreeOnlyForTheLossItself() {
        int missing = piece();
        itemCustodyService.markLost(missing, null, null);

        refusedWith(
                InventoryRefusal.CUSTODY_PIECE_LOST,
                () -> itemCustodyService.requireFreeToActOn(missing, Allowance.NONE));
        assertDoesNotThrow(() -> itemCustodyService.requireFreeToActOn(missing, Allowance.THE_LOSS_ITSELF));
    }

    /** Taking back a missing piece used to end the loss without anybody having found it. */
    @Test
    void writingOverALossIsRefusedAndFindingItEndsIt() {
        int missing = pieceWithMember();
        itemCustodyService.markLost(missing, null, null);

        refusedWith(InventoryRefusal.CUSTODY_PIECE_LOST, () -> itemCustodyService.takeBack(missing));
        refusedWith(
                InventoryRefusal.CUSTODY_PIECE_LOST,
                () -> itemCustodyService.applyStepCustody(missing, ItemCustody.AT_STATION, null, null));
        refusedWith(InventoryRefusal.CUSTODY_PIECE_LOST, () -> itemCustodyService.lendToPartner(missing, partner.id()));
        assertEquals(
                ItemCustody.LOST,
                inventoryRepo.findItemById(missing).orElseThrow().custody());

        itemCustodyService.markFound(missing);
        var found = inventoryRepo.findItemById(missing).orElseThrow();
        assertEquals(ItemCustody.WITH_MEMBER, found.custody(), "it turns up with whoever it was on");
        assertNull(found.lostAt());
    }

    /** A piece going back to an owner the station has left stays missing, now on the owner's books only. */
    @Test
    void aMissingPieceStaysMissingWhenItGoesBackToItsOwner() {
        int missing = pieceWithMember();
        itemCustodyService.markLost(missing, null, null);

        itemCustodyService.returnToOwner(missing);

        var item = inventoryRepo.findItemById(missing).orElseThrow();
        assertEquals(ItemCustody.LOST, item.custody());
        assertNull(item.assignedTo());
    }

    @Test
    void aPromisedPieceIsFreeOnlyToItsOwnMovement() {
        int promised = piece();
        ItemMovement planned = plannedHandOut(promised);

        refusedWith(
                InventoryRefusal.CUSTODY_PIECE_PROMISED,
                () -> itemCustodyService.requireFreeToActOn(promised, Allowance.NONE));
        assertDoesNotThrow(
                () -> itemCustodyService.requireFreeToActOn(promised, Allowance.walking(planned.id(), false)));
    }

    @Test
    void aPieceSettingOutIsFreeOnlyToItsOwnMovement() {
        int settingOut = pieceWithMember();
        ItemMovement swap = exchange(settingOut);

        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> itemCustodyService.requireFreeToActOn(settingOut, Allowance.NONE));
        assertDoesNotThrow(
                () -> itemCustodyService.requireFreeToActOn(settingOut, Allowance.walking(swap.id(), false)));
    }

    /** The further pieces of a dispatch were invisible to every check, since the movement names only one. */
    @Test
    void aFurtherPieceOfADispatchIsCarriedToo() {
        ItemMovement swap = exchange(pieceWithMember());
        int extra = piece();
        itemMovementItemRepo.add(swap.id(), StepSubject.INCOMING, List.of(extra));

        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> itemCustodyService.requireFreeToActOn(extra, Allowance.NONE));
        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> itemCustodyService.assignToMember(extra, member.id(), "Gu Ard"));
        assertDoesNotThrow(() -> itemCustodyService.requireFreeToActOn(extra, Allowance.walking(swap.id(), false)));
    }

    /** Another movement cannot name a piece a dispatch already carries. */
    @Test
    void aFurtherPieceOfADispatchCannotBeNamedByAnotherMovement() {
        ItemMovement swap = exchange(pieceWithMember());
        int extra = piece();
        itemMovementItemRepo.add(swap.id(), StepSubject.INCOMING, List.of(extra));

        refusedWith(InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT, () -> plannedHandOut(extra));
    }

    @Test
    void aPieceSetAsideForALoanIsNotFree() {
        int approved = piece();
        setAsideForALoan(approved, LendingStatus.APPROVED, LocalDate.now().plusDays(3));

        refusedWith(
                InventoryRefusal.CUSTODY_PIECE_SET_ASIDE_FOR_A_LOAN,
                () -> itemCustodyService.requireFreeToActOn(approved, Allowance.NONE));
        refusedWith(
                InventoryRefusal.CUSTODY_PIECE_SET_ASIDE_FOR_A_LOAN,
                () -> itemCustodyService.assignToMember(approved, member.id(), "Gu Ard"));
    }

    /** A loan only asked for, or one that is over, holds nothing. */
    @Test
    void aLoanOnlyAskedForOrOverHoldsNothing() {
        int asked = piece();
        setAsideForALoan(asked, LendingStatus.REQUESTED, LocalDate.now().plusDays(3));
        int over = piece();
        setAsideForALoan(over, LendingStatus.APPROVED, LocalDate.now().minusDays(1));

        assertDoesNotThrow(() -> itemCustodyService.requireFreeToActOn(asked, Allowance.NONE));
        assertDoesNotThrow(() -> itemCustodyService.requireFreeToActOn(over, Allowance.NONE));
    }

    /**
     * A movement whose piece went missing on the way closes all the same, and the piece stays missing on
     * the record it was on: closing is no finding.
     */
    @Test
    void aMovementWhosePieceWentMissingClosesAndLeavesItMissing() {
        int cancelled = pieceWithMember();
        ItemMovement first = exchange(cancelled);
        itemCustodyService.markLost(cancelled, null, null);
        int declined = pieceWithMember();
        ItemMovement second = exchange(declined);
        itemCustodyService.markLost(declined, null, null);
        int abandoned = pieceWithMember();
        ItemMovement third = exchange(abandoned);
        itemCustodyService.markLost(abandoned, null, null);

        assertEquals(
                MovementState.CANCELLED,
                itemMovementService.cancel(first.id(), team, "Weg").state());
        assertEquals(
                MovementState.DECLINED,
                itemMovementService.decline(second.id(), team, "Weg").state());
        assertEquals(
                MovementState.CANCELLED,
                itemMovementService.abandon(third.id(), "Weg").state());

        for (int missing : List.of(cancelled, declined, abandoned)) {
            var item = inventoryRepo.findItemById(missing).orElseThrow();
            assertEquals(ItemCustody.LOST, item.custody(), "it is still missing");
            assertEquals(member.id(), item.assignedTo(), "on the record it was on");
        }
    }
}
