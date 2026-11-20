/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.CheckItemRequest;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCorrection;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every direct write on a piece asks the custody guard first, each with what it may find on the piece.
 */
class ItemCustodyWritersTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();
    private static final LendingRepository LENDING = new LendingRepository();

    private static Station station;
    private static Station partner;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static int containerId;
    private static ItemMovementService.Actor team;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("WritersStation");
        partner = stationRepo.create("WritersPartner");
        account = accountRepo.create("writers@test.com", "Wri", "Ter");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Helme", InventoryType.INTERNAL, false)
                .id();
        containerId = containerRepo
                .create(station.id(), null, null, "Regal", null, "", null)
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
                .createItem(inventoryId, "W-" + CODES.incrementAndGet(), "Helm", null, null, ItemOwner.STATION, null)
                .id();
    }

    private int pieceWithMember() {
        int id = piece();
        itemCustodyService.assignToMember(id, member.id(), "Wri Ter");
        return id;
    }

    private int lostPiece() {
        int id = pieceWithMember();
        itemCustodyService.markLost(id, null, null);
        return id;
    }

    private ItemMovement exchange(int itemId) {
        return itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Wri Ter",
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
                "Wri Ter",
                null,
                inventoryId,
                null,
                null,
                "Für den Dienst",
                team,
                itemId);
    }

    private void setAsideForALoan(int itemId) {
        var request = LENDING.createRequest(
                partner.uid(),
                station.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                member.id(),
                null,
                null,
                "");
        var line = LENDING.addRequestItem(request.id(), inventoryId, null, null, 1, null);
        LENDING.assignItem(line.id(), itemId);
        LENDING.updateRequestStatus(request.id(), LendingStatus.APPROVED);
    }

    private static void refusedWith(InventoryRefusal expected, Executable write) {
        assertEquals(expected, assertThrows(RefusalResponse.class, write).refusal());
    }

    private ItemCustody custodyOf(int itemId) {
        return inventoryRepo.findItemById(itemId).orElseThrow().custody();
    }

    @Test
    void aLossIsRefusedOnAPieceAMovementOrALoanHolds() {
        int settingOut = pieceWithMember();
        exchange(settingOut);
        int promised = piece();
        plannedHandOut(promised);
        int onLoan = piece();
        setAsideForALoan(onLoan);

        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> itemCustodyService.markLost(settingOut, null, null));
        refusedWith(InventoryRefusal.CUSTODY_PIECE_PROMISED, () -> itemCustodyService.markLost(promised, null, null));
        refusedWith(
                InventoryRefusal.CUSTODY_PIECE_SET_ASIDE_FOR_A_LOAN,
                () -> itemCustodyService.markLost(onLoan, null, null));
    }

    @Test
    void aLossIsWrittenOnAFreePieceAndAgainOnAMissingOne() {
        int free = pieceWithMember();
        itemCustodyService.markLost(free, "Weg", null);
        assertEquals(ItemCustody.LOST, custodyOf(free));

        assertDoesNotThrow(() -> itemCustodyService.markLost(free, "Immer noch weg", null));
    }

    @Test
    void unassigningIsRefusedForAPieceSettingOutAndAllowedOtherwise() {
        int settingOut = pieceWithMember();
        exchange(settingOut);
        int plain = pieceWithMember();

        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> inventoryService.assignItem(settingOut, null, null));
        refusedWith(InventoryRefusal.CUSTODY_PIECE_LOST, () -> inventoryService.assignItem(lostPiece(), null, null));

        inventoryService.assignItem(plain, null, null);
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(plain));
    }

    /** Writing off a missing piece is the usual end of a loss; deleting one a movement carries is not. */
    @Test
    void deletingIsRefusedForACarriedPieceAndAllowedForAMissingOne() {
        int settingOut = pieceWithMember();
        exchange(settingOut);
        int missing = lostPiece();

        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> inventoryService.deleteItem(settingOut, null));
        assertTrue(inventoryService.deleteItem(missing, null));
    }

    @Test
    void puttingAwayIsRefusedForWhatIsNotHereAndAllowedForAPieceOnLoan() {
        int missing = lostPiece();
        int lent = piece();
        itemCustodyService.lendToPartner(lent, partner.id());
        int settingOut = pieceWithMember();
        exchange(settingOut);
        int onLoan = piece();
        setAsideForALoan(onLoan);

        refusedWith(
                InventoryRefusal.CUSTODY_PIECE_LOST, () -> itemCustodyService.placeInContainer(missing, containerId));
        refusedWith(
                InventoryRefusal.CUSTODY_PIECE_NOT_HERE_TO_PUT_AWAY,
                () -> itemCustodyService.placeInContainer(lent, containerId));
        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> itemCustodyService.placeInContainer(settingOut, containerId));

        itemCustodyService.placeInContainer(onLoan, containerId);
        assertEquals(
                containerId, inventoryRepo.findItemById(onLoan).orElseThrow().containerId());
        assertDoesNotThrow(() -> itemCustodyService.placeInContainer(missing, null), "clearing a place claims nothing");
    }

    /** A piece is refiled under another inventory only while nothing else names it where it is. */
    @Test
    void movingToAnotherInventoryIsRefusedForACarriedPieceAndAllowedOtherwise() {
        int other = inventoryRepo
                .create(station.id(), "Helme alt", InventoryType.INTERNAL, false)
                .id();
        int settingOut = pieceWithMember();
        exchange(settingOut);
        int plain = piece();

        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> inventoryService.moveItem(settingOut, other, null));
        refusedWith(InventoryRefusal.CUSTODY_PIECE_LOST, () -> inventoryService.moveItem(lostPiece(), other, null));

        assertEquals(
                other,
                inventoryService.moveItem(plain, other, null).orElseThrow().inventoryId());
    }

    @Test
    void anOrdinaryMovementCannotSetOutWithAMissingPiece() {
        int missing = lostPiece();

        refusedWith(InventoryRefusal.CUSTODY_PIECE_LOST, () -> exchange(missing));
        assertDoesNotThrow(() -> exchange(pieceWithMember()));
    }

    /** A movement put right after its piece has set out on another chain cannot take it back. */
    @Test
    void aCorrectionCannotReopenAMovementAroundAPieceAnotherOneCarries() {
        int piece = pieceWithMember();
        ItemMovement first = exchange(piece);
        itemMovementService.cancel(first.id(), team, "Doch nicht");
        exchange(piece);

        var correction = new ItemMovementService.Correction(null, null, false, null);
        refusedWith(
                InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT,
                () -> itemMovementService.correct(first.id(), correction, team, "Doch weiter"));
        assertEquals(
                MovementState.CANCELLED,
                itemMovementRepo.findById(first.id()).orElseThrow().state());

        var closing = new ItemMovementService.Correction(null, null, false, MovementState.CANCELLED);
        assertDoesNotThrow(() -> itemMovementService.correct(first.id(), closing, team, "Nur der Vermerk"));
    }

    /** A walk reporting a piece missing that a movement carries is refused before any of it is written. */
    @Test
    void aCheckWalkIsRefusedWholeWhenItsMissingPieceIsCarried() {
        int settingOut = pieceWithMember();
        exchange(settingOut);
        int free = pieceWithMember();
        var before = inventoryCheckRepo.latestCheckForMember(member.id()).map(check -> check.id());

        var results = List.of(
                new CheckItemRequest(free, inventoryId, CheckResult.LOST, ""),
                new CheckItemRequest(settingOut, inventoryId, CheckResult.LOST, ""));
        var refused = assertThrows(
                RefusalResponse.class,
                () -> inventoryCheckService.completeCheck(station.id(), member.id(), member.id(), results));
        assertEquals(InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT, refused.refusal());
        var named = inventoryRepo.findItemById(settingOut).orElseThrow();
        assertEquals(
                RefusalDetail.text(named.name() + " " + named.internalId()),
                refused.detail(),
                "the refusal names the piece it means");

        assertEquals(
                before,
                inventoryCheckRepo.latestCheckForMember(member.id()).map(check -> check.id()),
                "no check was written");
        assertEquals(ItemCustody.WITH_MEMBER, custodyOf(free), "and no loss either");
    }

    /** A piece the same walk reported missing comes off the record and stays missing when it is corrected. */
    @Test
    void correctingAPieceTheWalkReportedMissingLeavesItMissing() {
        int missing = pieceWithMember();
        inventoryCheckService.completeCheck(
                station.id(),
                member.id(),
                member.id(),
                List.of(new CheckItemRequest(missing, inventoryId, CheckResult.LOST, "")));

        inventoryCheckService.correct(
                member.id(), new ItemCorrection(inventoryId, missing, null, null, null, "W-neu", null));

        var item = inventoryRepo.findItemById(missing).orElseThrow();
        assertEquals(ItemCustody.LOST, item.custody());
        assertNull(item.assignedTo());
        assertFalse(itemMovementService.isOnAMovement(missing));
    }
}
