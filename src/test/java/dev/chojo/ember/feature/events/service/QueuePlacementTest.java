/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.QueueEntry;
import dev.chojo.ember.feature.events.entity.QueueOrder;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueList;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuePlacementTest extends RepositoryTestBase {
    private static final LocalDate DATE = LocalDate.now().plusDays(10);

    private static final RegistrationQueueRepository queue = new RegistrationQueueRepository();
    private static QueuePlacement placement;
    private static Station station;
    private static final List<Account> accounts = new ArrayList<>();
    private static final List<StationMember> members = new ArrayList<>();

    private StationEvent event;
    private QueueList list;

    @BeforeAll
    static void setup() {
        placement = new QueuePlacement(queue, eventRegistrationRepo);
        station = stationRepo.create("Queue Placement Station");
        for (int i = 0; i < 4; i++) {
            var account = accountRepo.create("queue-placement-" + i + "@test.com", "Queue", "Member " + i);
            accounts.add(account);
            members.add(stationMemberRepo.create(station.id(), account.id()));
        }
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    @BeforeEach
    void createEvent() {
        var start = DATE.atTime(18, 0).toInstant(ZoneOffset.UTC);
        event = eventRepo.create(
                station.id(),
                "Maschinistenlehrgang",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                1,
                null,
                null);
        list = RegistrationQueueRepository.ofDate(event.id(), DATE);
    }

    @AfterEach
    void removeEvent() {
        eventRepo.delete(event.id());
    }

    private QueueOwner waiting(int member) {
        return QueueOwner.local(eventRegistrationRepo
                .create(event.id(), members.get(member).id(), DATE, RegistrationStatus.QUEUED, null)
                .id());
    }

    private List<Integer> order() {
        return queue.entries(list).stream().map(QueueEntry::localId).toList();
    }

    @Test
    void aListKeptByTimePutsEveryNewcomerAtTheEnd() {
        var first = waiting(0);
        var second = waiting(1);

        placement.place(list, QueueOrder.TIME, first, 10);
        placement.place(list, QueueOrder.TIME, second, 90);

        assertEquals(List.of(first.id(), second.id()), order());
        assertEquals(
                List.of(1, 2),
                queue.entries(list).stream().map(QueueEntry::rank).toList());
    }

    @Test
    void aListKeptByFairnessPutsAHigherScoreFirstAndKeepsTiesInTimeOrder() {
        var low = waiting(0);
        var tieFirst = waiting(1);
        var tieSecond = waiting(2);
        var high = waiting(3);

        placement.place(list, QueueOrder.FAIRNESS, low, 20);
        placement.place(list, QueueOrder.FAIRNESS, tieFirst, 50);
        placement.place(list, QueueOrder.FAIRNESS, tieSecond, 50);
        placement.place(list, QueueOrder.FAIRNESS, high, 80);

        assertEquals(List.of(high.id(), tieFirst.id(), tieSecond.id(), low.id()), order());
    }

    @Test
    void anEntryMovedByHandStaysWhereItWasPut() {
        var a = waiting(0);
        var b = waiting(1);
        var c = waiting(2);
        placement.place(list, QueueOrder.FAIRNESS, a, 60);
        placement.place(list, QueueOrder.FAIRNESS, b, 40);
        query("UPDATE registration_queue SET position = 5 WHERE registration_id = :id;")
                .single(call().bind("id", a.id()))
                .update();

        placement.place(list, QueueOrder.FAIRNESS, c, 50);

        assertEquals(List.of(c.id(), b.id(), a.id()), order(), "the newcomer goes before the first lower score");
    }

    @Test
    void aSignUpAlreadyWaitingKeepsItsPlace() {
        var a = waiting(0);
        var b = waiting(1);
        placement.place(list, QueueOrder.TIME, a, 50);
        placement.place(list, QueueOrder.TIME, b, 50);

        var again = placement.place(list, QueueOrder.TIME, a, 99);

        assertEquals(1, again.rank());
        assertEquals(List.of(a.id(), b.id()), order());
    }

    @Test
    void anEntryLeavingLeavesAGapAndTheRanksCloseUp() {
        var a = waiting(0);
        var b = waiting(1);
        placement.place(list, QueueOrder.TIME, a, 50);
        placement.place(list, QueueOrder.TIME, b, 50);

        assertTrue(queue.remove(list, a));
        assertFalse(queue.remove(list, a));

        var left = queue.entries(list).getFirst();
        assertEquals(2, left.position());
        assertEquals(1, left.rank());
    }

    @Test
    void theScoreReadsTheMembersHistoryAndANewcomerScoresFifty() {
        var past = eventRepo.create(
                station.id(),
                "Vorher",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().minusSeconds(86_400L * 20),
                Instant.now().minusSeconds(86_400L * 20 - 3600),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        try {
            eventRegistrationRepo.create(
                    past.id(), members.get(0).id(), LocalDate.now().minusDays(20), RegistrationStatus.DENIED, null);

            assertEquals(
                    105.0, placement.scoreOf(station.id(), null, members.get(0).id()));
            assertEquals(
                    50.0, placement.scoreOf(station.id(), null, members.get(1).id()));
            assertEquals(50.0, placement.scoreOf(station.id(), null, null));
        } finally {
            eventRepo.delete(past.id());
        }
    }

    @Test
    void anEventGroupKeepsItsOwnList() {
        var groupList = RegistrationQueueRepository.ofEventGroup(Integer.MAX_VALUE);

        assertTrue(queue.entries(groupList).isEmpty());
        assertEquals(1, queue.endOf(groupList));
        assertEquals(QueueOrder.TIME, queue.orderOfEventGroup(Integer.MAX_VALUE));
        assertEquals(QueueOrder.TIME, queue.orderOfEvent(event.id()));
    }

    private static StationEvent pastAt(int stationId, @Nullable Integer categoryId, int daysAgo) {
        var start = Instant.now().minusSeconds(86_400L * daysAgo);
        return eventRepo.create(
                stationId,
                "Vorher " + daysAgo,
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(3600),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                categoryId,
                null,
                null,
                null);
    }

    @Test
    void theScoreOnlyReadsHistoryOfTheStationTheCategoryAndTheLastYear() {
        var category = eventCategoryRepo.create(station.id(), "Lehrgang", 0, null);
        var other = eventCategoryRepo.create(station.id(), "Dienst", 1, null);
        var elsewhere = stationRepo.create("Queue Placement Elsewhere");
        int member = members.get(2).id();
        var inCategory = pastAt(station.id(), category.id(), 20);
        var inOther = pastAt(station.id(), other.id(), 20);
        var atOtherStation = pastAt(elsewhere.id(), null, 20);
        var longAgo = pastAt(station.id(), category.id(), 400);
        try {
            eventRegistrationRepo.create(
                    inCategory.id(), member, LocalDate.now().minusDays(20), RegistrationStatus.DENIED, null);
            eventRegistrationRepo.create(
                    inOther.id(), member, LocalDate.now().minusDays(20), RegistrationStatus.ACCEPTED, null);
            eventRegistrationRepo.create(
                    atOtherStation.id(), member, LocalDate.now().minusDays(20), RegistrationStatus.DENIED, null);
            eventRegistrationRepo.create(
                    longAgo.id(), member, LocalDate.now().minusDays(400), RegistrationStatus.DENIED, null);

            assertEquals(105.0, placement.scoreOf(station.id(), category.id(), member), "one denial in the category");
            assertEquals(48.0, placement.scoreOf(station.id(), other.id(), member), "one acceptance in the other");
            assertEquals(
                    78.0, placement.scoreOf(station.id(), null, member), "both, nothing from elsewhere or long ago");
        } finally {
            eventRepo.delete(inCategory.id());
            eventRepo.delete(inOther.id());
            eventRepo.delete(atOtherStation.id());
            eventRepo.delete(longAgo.id());
            stationRepo.delete(elsewhere.id());
        }
    }
}
