/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
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
                exchangeService, inventoryService, lostAndFoundRepo, profileFieldRepo, stationRepo);
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
                StringNode.valueOf(LocalDate.now().minusDays(2).toString()));

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
        var exchange = exchangeService.create(
                station.id(), member.id(), "Check Notes", item.id(), inventory.id(), null, null, "Zu klein", null);

        var forReader = service.findForStation(station.id(), Set.of(StationPermission.INVENTORY_READ));
        var swap = forReader.get(member.id()).swaps().getFirst();
        assertEquals(ExchangeStatus.ANNOUNCED, swap.status());
        assertEquals(ExchangeStatus.RECEIVED, swap.nextStatus());
        assertFalse(swap.handOverNext(), "the member still has the old piece, so nothing is handed over yet");
        assertEquals("Einsatzjacke", swap.inventoryName());

        var forTicker = service.findForStation(station.id(), Set.of());
        assertFalse(forTicker.containsKey(member.id()), "a reader without the inventory is told nothing");

        itemMovementService.abandon(exchange.id(), "Test vorbei");
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
     * A swap out of the station's own store skips the two postal steps, so the move after the old
     * piece comes in is the handover itself. This is the case the note exists for.
     */
    @Test
    void anInternalSwapHandsOverStraightAfterTheOldPieceComesIn() {
        assertEquals(
                ExchangeStatus.DONE,
                MemberCheckNotesService.nextStatus(ExchangeStatus.RECEIVED, InventoryType.INTERNAL));
        assertEquals(
                ExchangeStatus.RECEIVED,
                MemberCheckNotesService.nextStatus(ExchangeStatus.ANNOUNCED, InventoryType.INTERNAL));
    }

    /**
     * A swap that goes away and comes back passes the two postal steps first, so only the piece
     * having arrived means the next move is the handover.
     */
    @Test
    void anExternalSwapHandsOverOnlyOnceThePieceHasArrived() {
        assertEquals(
                ExchangeStatus.SHIPPED,
                MemberCheckNotesService.nextStatus(ExchangeStatus.RECEIVED, InventoryType.EXTERNAL));
        assertEquals(
                ExchangeStatus.DONE, MemberCheckNotesService.nextStatus(ExchangeStatus.ARRIVED, InventoryType.MIXED));
    }

    /**
     * A swap at its end takes no further step, and neither does one standing on an end that is not
     * part of the walk at all.
     */
    @Test
    void aSwapThatIsOverTakesNoFurtherStep() {
        assertNull(MemberCheckNotesService.nextStatus(ExchangeStatus.DONE, InventoryType.EXTERNAL));
        assertNull(MemberCheckNotesService.nextStatus(ExchangeStatus.CANCELLED, InventoryType.EXTERNAL));
        assertNull(MemberCheckNotesService.nextStatus(ExchangeStatus.DECLINED, InventoryType.INTERNAL));
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
