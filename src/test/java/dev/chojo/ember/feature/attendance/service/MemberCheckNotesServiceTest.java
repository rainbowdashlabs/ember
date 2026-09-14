/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberCheckNotesServiceTest extends RepositoryTestBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    private static MemberCheckNotesService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setupNotes() {
        service = new MemberCheckNotesService(
                itemMovementService, inventoryService, lostAndFoundRepo, profileFieldRepo, stationRepo);
        station = stationRepo.create("CheckNotesStation");
        account = accountRepo.create("check-notes@test.com", "Check", "Notes");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanupNotes() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /**
     * A found item somebody claimed is named to whoever keeps the lost and found, and not to anybody
     * else. Taking an attendance says nothing about being allowed to know what a member lost.
     */
    @Test
    void aClaimedFindIsNamedOnlyToWhoeverKeepsTheLostAndFound() {
        var item = lostAndFoundRepo.create(station.id(), "Blaue Trinkflasche", LocalDate.now(), member.id());
        lostAndFoundRepo.claim(item.id(), member.id());

        var forKeeper = service.findForStation(station.id(), Set.of(StationPermission.LOST_AND_FOUND_MANAGE));
        assertTrue(forKeeper.containsKey(member.id()));
        assertEquals(
                "Blaue Trinkflasche",
                forKeeper.get(member.id()).foundItems().getFirst().description());

        var forTicker = service.findForStation(station.id(), Set.of());
        assertFalse(forTicker.containsKey(member.id()), "somebody who only ticks names off is told nothing");

        lostAndFoundRepo.delete(item.id());
    }

    /**
     * A birthday is answered from the station's own birth date field, and only where that field is
     * one the reader may see. A station that keeps it to managers tells nobody else.
     */
    /**
     * Two days ago as the station reckons it, which is not the same as two days ago here.
     *
     * <p>A station keeps its own timezone and defaults to Europe/Berlin, so between 22:00 and
     * midnight UTC the station is already on the next day. A test that wrote the date in the JVM's
     * own zone therefore failed every night on a runner set to UTC, and it was the test that was
     * wrong rather than the service.
     */
    private static LocalDate twoDaysAgoAtTheStation() {
        return LocalDate.now(StationFormat.timezoneOf(
                        stationRepo.findById(station.id()).orElseThrow()))
                .minusDays(2);
    }

    @Test
    void aBirthdayFollowsTheScopeOfTheFieldItLivesIn() {
        var field = profileFieldRepo.create(
                station.id(),
                "Geburtstag",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MANAGER);
        profileFieldRepo.setValue(
                member.id(),
                field.id(),
                StringNode.valueOf(twoDaysAgoAtTheStation().toString()));

        var forManager = service.findForStation(station.id(), Set.of(StationPermission.STATION_ADMINISTRATOR));
        assertEquals(2, forManager.get(member.id()).birthdayDaysAgo());

        var forMember = service.findForStation(station.id(), Set.of(StationPermission.USER));
        assertFalse(forMember.containsKey(member.id()), "a field kept to managers is kept from a member here too");

        profileFieldRepo.delete(field.id());
    }

    /**
     * A swap that has not finished is named to whoever may read the inventory, saying where it
     * stands and what it does next, and to nobody else.
     *
     * <p>A swap just announced is waiting on the member, so the next move is not the handover. That
     * distinction is the whole point of the note.
     */
    @Test
    void anOpenSwapIsNamedWithWhatItDoesNext() {
        var inventory = inventoryService.create(station.id(), "Einsatzjacke", InventoryType.INTERNAL, false, true);
        var item = inventoryRepo.createItem(inventory.id(), "EJ-1", "Einsatzjacke", null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        var exchange = swapOf(item.id(), inventory.id(), "Zu klein");

        var forReader = service.findForStation(station.id(), Set.of(StationPermission.INVENTORY_READ));
        var swap = forReader.get(member.id()).swaps().getFirst();
        assertEquals(MovementPurpose.EXCHANGE, swap.purpose());
        assertEquals(exchange.currentStepId(), swap.stepId(), "the step it stands on is the one to acknowledge");
        assertEquals(StepActor.STATION, swap.stepActor(), "and the station takes the piece in next");
        assertFalse(swap.stepLabel().isBlank(), "named in the words the chain gives it");
        assertFalse(swap.handOverNext(), "the member still has the old piece, so nothing is handed over yet");
        assertEquals("Einsatzjacke", swap.inventoryName());

        var forTicker = service.findForStation(station.id(), Set.of());
        assertFalse(forTicker.containsKey(member.id()), "a reader without the inventory is told nothing");

        itemMovementService.abandon(exchange.id(), "Test vorbei");
    }

    /** A swap of this member's, raised the way every screen raises one. */
    private dev.chojo.ember.feature.inventory.entity.ItemMovement swapOf(int itemId, int inventoryId, String reason) {
        return itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Check Notes",
                itemId,
                inventoryId,
                null,
                null,
                reason,
                new ItemMovementService.Actor(member.id(), true),
                null);
    }

    /** Acknowledges the step the movement stands on, as the station. */
    private dev.chojo.ember.feature.inventory.entity.ItemMovement walkOnce(
            dev.chojo.ember.feature.inventory.entity.ItemMovement movement, Integer picked) {
        return itemMovementService.acknowledge(
                movement.id(), movement.currentStepId(), new ItemMovementService.Actor(member.id(), true), "", picked);
    }

    /**
     * A swap of the association's gear leaves the sheet the moment the old piece is in. The two
     * postal steps run between the station and the association, so naming them beside a name is work
     * for somebody who has nothing to do with it. It is named again once the replacement is here and
     * the next move is putting it into their hands.
     */
    @Test
    void aSwapPassingThroughThePostIsNamedOnlyAtItsTwoEnds() {
        var inventory = inventoryService.create(station.id(), "Handschuhe", InventoryType.MIXED, false, true);
        var item = inventoryRepo.createItem(inventory.id(), "HS-1", "Handschuhe", null, null, ItemOwner.CLUSTER, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        var replacement =
                inventoryRepo.createItem(inventory.id(), "HS-2", "Handschuhe", null, null, ItemOwner.CLUSTER, null);
        var exchange = swapOf(item.id(), inventory.id(), "Kaputt");

        assertTrue(namesSwap(exchange.id()), "the member is still wearing the old piece");

        // Taken in at the station, and then posted to the body above: the two steps the member is not part of.
        var walked = walkOnce(exchange, null);
        walked = walkOnce(walked, null);
        assertFalse(namesSwap(exchange.id()), "the piece is between the station and the association");

        // Received by the body, which then sends the replacement, and the station takes it in.
        walked = walkOnce(walked, null);
        walked = walkOnce(walked, replacement.id());
        walkOnce(walked, null);
        assertTrue(namesSwap(exchange.id()), "the next move hands the replacement over");

        itemMovementService.abandon(exchange.id(), "Test vorbei");
        inventoryRepo.delete(inventory.id());
    }

    /**
     * The note names the piece the step is about and the size written on it, which is what somebody
     * standing at a shelf reads off it. The inventory it came out of is not that: a station with four
     * inventories of jackets says "Einsatzjacke" four times and names none of them.
     */
    @Test
    void theNoteNamesThePieceAndTheSizeWrittenOnIt() {
        var inventory = inventoryService.create(station.id(), "Einsatzjacke gross", InventoryType.INTERNAL, true, true);
        inventoryRepo.createSize(inventory.id(), "52", 0, null);
        int sizeId = inventoryRepo.findSizes(inventory.id()).getFirst().id();
        var item = inventoryRepo.createItem(inventory.id(), "EJ-1", "Einsatzjacke 04", sizeId, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        var exchange = swapOf(item.id(), inventory.id(), "Zu klein");

        var swap = service.findForStation(station.id(), Set.of(StationPermission.INVENTORY_READ))
                .get(member.id())
                .swaps()
                .getFirst();

        assertEquals("Einsatzjacke 04", swap.itemName(), "the piece, not the drawer it came out of");
        assertEquals("52", swap.itemSize());
        assertFalse(swap.stepLabel().isBlank(), "and the words of the step the button will carry");

        itemMovementService.abandon(exchange.id(), "Test vorbei");
        inventoryRepo.delete(inventory.id());
    }

    /**
     * A piece out of an inventory that keeps no sizes carries none, and the note says nothing rather
     * than an empty badge.
     */
    @Test
    void aPieceWithoutASizeCarriesNone() {
        var inventory = inventoryService.create(station.id(), "Helm ohne Groesse", InventoryType.INTERNAL, false, true);
        var item = inventoryRepo.createItem(inventory.id(), "HE-1", "Helm 02", null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        var exchange = swapOf(item.id(), inventory.id(), "Kaputt");

        var swap = service.findForStation(station.id(), Set.of(StationPermission.INVENTORY_READ))
                .get(member.id())
                .swaps()
                .getFirst();

        assertNull(swap.itemSize());
        assertEquals("Helm 02", swap.itemName());

        itemMovementService.abandon(exchange.id(), "Test vorbei");
        inventoryRepo.delete(inventory.id());
    }

    /** Whether the sheet names this movement beside the member, read as somebody who may see them. */
    private boolean namesSwap(int movementId) {
        var notes = service.findForStation(station.id(), Set.of(StationPermission.INVENTORY_READ))
                .get(member.id());
        return notes != null && notes.swaps().stream().anyMatch(swap -> swap.movementId() == movementId);
    }

    /**
     * A station where nothing is outstanding answers nothing at all, rather than a row a member
     * saying so.
     */
    @Test
    void aStationWithNothingOutstandingAnswersNothing() {
        assertTrue(service.findForStation(station.id(), Set.of(StationPermission.INVENTORY_READ))
                .isEmpty());
    }

    /**
     * A swap out of the station's own store never leaves the building, so the member is named at every
     * stage of it. This is the case the note exists for: the handover is the next move, and whoever has
     * the member in front of them can make it.
     */
    @Test
    void anInternalSwapStaysNamedUntilTheReplacementIsHandedOver() {
        var inventory = inventoryService.create(station.id(), "Stiefel", InventoryType.INTERNAL, false, true);
        var item = inventoryRepo.createItem(inventory.id(), "ST-1", "Stiefel", null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        var replacement = inventoryRepo.createItem(inventory.id(), "ST-2", "Stiefel", null, null);
        var exchange = swapOf(item.id(), inventory.id(), "Zu klein");

        // The station takes the old pair in, then puts the replacement aside.
        var walked = walkOnce(exchange, null);
        walked = walkOnce(walked, replacement.id());

        var swap = service.findForStation(station.id(), Set.of(StationPermission.INVENTORY_READ))
                .get(member.id())
                .swaps()
                .getFirst();
        assertTrue(swap.handOverNext(), "the next move puts the replacement into their hands");
        assertEquals(replacement.id(), swap.replacementItemId(), "and the sheet knows which pair that is");

        itemMovementService.abandon(walked.id(), "Test vorbei");
        inventoryRepo.delete(inventory.id());
    }

    /**
     * Once the piece has been handed over, what is left is the member saying they have it, and that is
     * theirs to say.
     *
     * <p>That step leaves the piece with the member too. Reading it as another handover kept the swap on
     * the sheet with a button that would have answered for them.
     */
    @Test
    void nothingIsLeftOnTheSheetOnceThePieceHasBeenHandedOver() {
        var inventory = inventoryService.create(station.id(), "Jacke", InventoryType.INTERNAL, false, true);
        var item = inventoryRepo.createItem(inventory.id(), "JA-1", "Jacke", null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        var replacement = inventoryRepo.createItem(inventory.id(), "JA-2", "Jacke", null, null);
        var exchange = swapOf(item.id(), inventory.id(), "Zu klein");

        var walked = walkOnce(exchange, null);
        walked = walkOnce(walked, replacement.id());
        var handed = walkOnce(walked, null);

        assertEquals(
                StepActor.MEMBER,
                itemMovementService.stepsOf(handed).stream()
                        .filter(step -> handed.currentStepId() != null && step.id() == handed.currentStepId())
                        .findFirst()
                        .orElseThrow()
                        .actor(),
                "the member confirms what they are holding");
        assertFalse(
                service.findForStation(station.id(), Set.of(StationPermission.INVENTORY_READ))
                        .containsKey(member.id()),
                "and the sheet has nothing left to offer whoever is ticking off names");

        itemMovementService.abandon(handed.id(), "Test vorbei");
        inventoryRepo.delete(inventory.id());
    }

    /** A movement that has finished is named to nobody, because there is no step left to acknowledge. */
    @Test
    void aSwapThatIsOverIsNotNamedAtAll() {
        var inventory = inventoryService.create(station.id(), "Mütze", InventoryType.INTERNAL, false, true);
        var item = inventoryRepo.createItem(inventory.id(), "MZ-1", "Mütze", null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        var exchange = swapOf(item.id(), inventory.id(), "Zu klein");

        assertTrue(namesSwap(exchange.id()));

        itemMovementService.cancel(exchange.id(), new ItemMovementService.Actor(member.id(), true), "Doch nicht");

        assertFalse(namesSwap(exchange.id()), "a movement that stopped is nothing to do in the room");

        inventoryRepo.delete(inventory.id());
    }

    /**
     * Today reads as zero rather than as nothing, because "has a birthday today" is the one the
     * evening is actually for.
     */
    @Test
    void aBirthdayTodayIsNoDaysAgo() {
        assertEquals(0, MemberCheckNotesService.daysSinceBirthday("2011-09-03", TODAY));
    }

    @Test
    void aBirthdayWithinTheWindowCountsTheDays() {
        assertEquals(1, MemberCheckNotesService.daysSinceBirthday("2011-09-02", TODAY));
        assertEquals(6, MemberCheckNotesService.daysSinceBirthday("2000-08-28", TODAY));
    }

    /**
     * A day past the window is nothing at all, and so is a birthday still to come: the note is about
     * what has just happened, not what is due.
     */
    @Test
    void aBirthdayOutsideTheWindowIsNotWorthSaying() {
        assertNull(MemberCheckNotesService.daysSinceBirthday("2000-08-27", TODAY));
        assertNull(MemberCheckNotesService.daysSinceBirthday("2000-09-04", TODAY));
        assertNull(MemberCheckNotesService.daysSinceBirthday("2000-03-01", TODAY));
    }

    /**
     * The anniversary is what counts, not the date. A birthday in late December is a few days ago in
     * early January, where comparing inside one calendar year would make it most of a year.
     */
    @Test
    void aBirthdayOverTheTurnOfTheYearIsStillDaysAgo() {
        assertEquals(6, MemberCheckNotesService.daysSinceBirthday("1998-12-30", LocalDate.of(2026, 1, 5)));
        assertEquals(0, MemberCheckNotesService.daysSinceBirthday("1998-12-31", LocalDate.of(2026, 12, 31)));
    }

    /**
     * Somebody born on a leap day has a birthday in every year, not one year in four. It is counted
     * against the 28th where February has no 29th.
     */
    @Test
    void aLeapDayBirthdayIsFoundInAnOrdinaryYear() {
        assertEquals(0, MemberCheckNotesService.daysSinceBirthday("2000-02-29", LocalDate.of(2027, 2, 28)));
        assertEquals(0, MemberCheckNotesService.daysSinceBirthday("2000-02-29", LocalDate.of(2028, 2, 29)));
    }

    /**
     * A profile answer is whatever somebody typed. An answer that is not a date is no birthday, and
     * must not stop the rest of the sheet being answered.
     */
    @Test
    void anAnswerThatIsNotADateIsNoBirthday() {
        assertNull(MemberCheckNotesService.daysSinceBirthday(null, TODAY));
        assertNull(MemberCheckNotesService.daysSinceBirthday("", TODAY));
        assertNull(MemberCheckNotesService.daysSinceBirthday("irgendwann", TODAY));
        assertNull(MemberCheckNotesService.daysSinceBirthday("03.09.2011", TODAY));
    }

    /**
     * Answers are stored as JSON, so a date arrives wrapped in quotes.
     */
    @Test
    void aQuotedDateIsReadAsADate() {
        assertEquals(0, MemberCheckNotesService.daysSinceBirthday("\"2011-09-03\"", TODAY));
    }
}
