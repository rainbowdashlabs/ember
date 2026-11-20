/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the custody guard reads off the rows: a loss that only the loss and its finding may touch, the
 * further pieces of a dispatch, and pieces set aside for a loan.
 */
class ItemCustodyGuardRepositoryTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();
    private static final LendingRepository LENDING = new LendingRepository();

    private static Station station;
    private static Station partner;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("GuardRepoStation");
        partner = stationRepo.create("GuardRepoPartner");
        account = accountRepo.create("guardrepo@test.com", "Gu", "Ard");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Lampen", InventoryType.INTERNAL, false)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(partner.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int piece() {
        return inventoryRepo
                .createItem(inventoryId, "L-" + CODES.incrementAndGet(), "Lampe", null, null, ItemOwner.STATION, null)
                .id();
    }

    @Test
    void anOrdinaryWriteLeavesALossAlone() {
        int missing = piece();
        assertTrue(inventoryRepo.updateCustody(missing, ItemCustody.LOST, station.id(), null, null));

        assertFalse(
                inventoryRepo.updateCustody(missing, ItemCustody.WITH_OWNER, null, null, null),
                "nothing is written over the loss");
        var item = inventoryRepo.findItemById(missing).orElseThrow();
        assertEquals(ItemCustody.LOST, item.custody());
        assertNotNull(item.lostAt());
    }

    @Test
    void findingAPieceAgainEndsTheLoss() {
        int missing = piece();
        inventoryRepo.updateCustody(missing, ItemCustody.LOST, station.id(), null, null);

        assertTrue(inventoryRepo.updateCustodyFoundAgain(missing, ItemCustody.WITH_OWNER, null, null));

        var item = inventoryRepo.findItemById(missing).orElseThrow();
        assertEquals(ItemCustody.WITH_OWNER, item.custody());
        assertNull(item.lostAt());
    }

    @Test
    void recordingTheLossAgainKeepsWhenItWasFirstMissed() {
        int missing = piece();
        inventoryRepo.updateCustody(missing, ItemCustody.LOST, station.id(), null, null);
        var first = inventoryRepo.findItemById(missing).orElseThrow().lostAt();

        assertTrue(inventoryRepo.updateCustody(missing, ItemCustody.LOST, station.id(), null, null));
        assertEquals(first, inventoryRepo.findItemById(missing).orElseThrow().lostAt());
    }

    /** A further piece of a dispatch is on its movement for every check, and off it once it closes. */
    @Test
    void aFurtherPieceOfADispatchIsOnItsMovement() {
        int named = piece();
        itemCustodyService.assignToMember(named, member.id(), "Gu Ard");
        ItemMovement swap = itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Gu Ard",
                named,
                inventoryId,
                null,
                null,
                "Kaputt",
                new ItemMovementService.Actor(member.id(), true),
                null);
        int extra = piece();
        itemMovementItemRepo.add(swap.id(), StepSubject.INCOMING, List.of(extra));

        assertTrue(itemMovementRepo.isOnAMovement(extra));
        assertEquals(List.of(swap.id()), itemMovementRepo.findOpenCarrying(extra));
        assertEquals(List.of(swap.id()), itemMovementRepo.findOpenCarrying(named));
        assertTrue(
                inventoryRepo.findFreeStock(inventoryId, null).stream().noneMatch(item -> item.id() == extra),
                "and it is not free stock");

        itemMovementService.cancel(swap.id(), new ItemMovementService.Actor(member.id(), true), "Doch nicht");
        assertFalse(itemMovementRepo.isOnAMovement(extra), "a closed movement carries nothing");
        assertTrue(itemMovementRepo.findOpenCarrying(extra).isEmpty());
    }

    @Test
    void aPieceIsSetAsideOnlyByAnApprovedOrRunningLoanThatIsNotOver() {
        int approved = piece();
        int lent = piece();
        int asked = piece();
        int over = piece();
        int free = piece();
        loan(approved, LendingStatus.APPROVED, LocalDate.now().plusDays(2));
        loan(lent, LendingStatus.LENT, null);
        loan(asked, LendingStatus.REQUESTED, LocalDate.now().plusDays(2));
        loan(over, LendingStatus.APPROVED, LocalDate.now().minusDays(1));

        assertTrue(inventoryRepo.isSetAsideForALoan(approved));
        assertTrue(inventoryRepo.isSetAsideForALoan(lent), "a loan without an end is not over");
        assertFalse(inventoryRepo.isSetAsideForALoan(asked), "a loan only asked for holds nothing yet");
        assertFalse(inventoryRepo.isSetAsideForALoan(over), "a loan that is over holds nothing");
        assertFalse(inventoryRepo.isSetAsideForALoan(free));
    }

    private int loan(int itemId, LendingStatus status, LocalDate until) {
        var request = LENDING.createRequest(
                partner.uid(), station.uid(), LocalDate.now().minusDays(3), until, member.id(), null, null, "");
        var line = LENDING.addRequestItem(request.id(), inventoryId, null, null, 1, null);
        LENDING.assignItem(line.id(), itemId);
        LENDING.updateRequestStatus(request.id(), status);
        return request.id();
    }

    /** The loan holding a piece does not hold it against itself, and holds it against every other. */
    @Test
    void aPieceIsHeldAgainstEveryLoanButItsOwn() {
        int radio = piece();
        int holding = loan(radio, LendingStatus.APPROVED, LocalDate.now().plusDays(2));
        int other = LENDING.createRequest(
                        partner.uid(), station.uid(), LocalDate.now(), null, member.id(), null, null, "")
                .id();

        assertFalse(inventoryRepo.isSetAsideForAnotherLoan(radio, holding));
        assertTrue(inventoryRepo.isSetAsideForAnotherLoan(radio, other));
    }

    /** What a picker offers is what the guard lets through: on the shelf, carried by nothing, held by no loan. */
    @Test
    void onlyPiecesFreeToHandOutAreOffered() {
        int free = piece();
        int withMember = piece();
        itemCustodyService.assignToMember(withMember, member.id(), "Gu Ard");
        int missing = piece();
        itemCustodyService.markLost(missing, null, null);
        int promised = piece();
        itemMovementService.create(
                station.id(),
                MovementPurpose.ISSUE,
                member.id(),
                "Gu Ard",
                null,
                inventoryId,
                null,
                null,
                "Für den Dienst",
                new ItemMovementService.Actor(member.id(), true),
                promised);
        int onLoan = piece();
        loan(onLoan, LendingStatus.APPROVED, LocalDate.now().plusDays(2));

        var offered = inventoryRepo.findFreeToHandOut(inventoryId).stream()
                .map(item -> item.id())
                .toList();
        assertTrue(offered.contains(free));
        for (int notFree : List.of(withMember, missing, promised, onLoan)) {
            assertFalse(offered.contains(notFree), "piece " + notFree + " is not free to hand out");
        }
    }
}
