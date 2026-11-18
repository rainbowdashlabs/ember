/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestEventPlaces;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What whoever runs an appointment does with its waiting list by hand. */
class QueueManagerActionsTest extends RepositoryTestBase {
    private static final LocalDate DATE = LocalDate.now().plusDays(9);

    private static EventRegistrationService registrations;
    private static Station station;
    private static final List<Account> accounts = new ArrayList<>();
    private static final List<StationMember> members = new ArrayList<>();
    private final List<StationEvent> events = new ArrayList<>();

    @BeforeAll
    static void setup() {
        registrations = new EventRegistrationService(
                eventRegistrationRepo,
                new EventRegistrationFieldRepository(),
                eventRepo,
                new DomainEventBus(Set.of()),
                memberNameResolver,
                TestEventPlaces.of(eventRegistrationRepo));
        station = stationRepo.create("Queue Manager Station");
        for (int i = 0; i < 4; i++) {
            var account = accountRepo.create("queue-manager-" + i + "@test.com", "Liste", "Mitglied " + i);
            accounts.add(account);
            members.add(stationMemberRepo.create(station.id(), account.id()));
        }
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    @AfterEach
    void removeEvents() {
        events.forEach(event -> eventRepo.delete(event.id()));
        events.clear();
    }

    private StationEvent event(@Nullable Integer limit, boolean requiresConfirmation) {
        var start = DATE.atTime(18, 0).toInstant(ZoneOffset.UTC);
        var event = eventRepo.create(
                station.id(),
                "Absturzsicherung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                true,
                null,
                requiresConfirmation,
                null,
                limit,
                null,
                null,
                null);
        events.add(event);
        return event;
    }

    private EventRegistration signUp(StationEvent event, int member) {
        return registrations.register(event.id(), members.get(member).id(), DATE, !event.requiresConfirmation(), null);
    }

    private RegistrationStatus statusOf(EventRegistration registration) {
        return eventRegistrationRepo.findById(registration.id()).orElseThrow().status();
    }

    private List<Integer> line(EventRegistration... registrationsInLine) {
        var ranks = registrations.queueRanksOf(
                Arrays.stream(registrationsInLine).map(EventRegistration::id).toList());
        return Arrays.stream(registrationsInLine)
                .sorted(Comparator.comparing(registration -> ranks.getOrDefault(registration.id(), 99)))
                .map(EventRegistration::id)
                .toList();
    }

    private void signedUpAt(EventRegistration registration, String at) {
        query("UPDATE event_registration SET created_at = CAST(:at AS TIMESTAMP) WHERE id = :id;")
                .single(call().bind("at", at).bind("id", registration.id()))
                .update();
    }

    @Test
    void pendingSignUpsJoinTheListInSignUpOrder() {
        var event = event(1, true);
        var later = signUp(event, 0);
        var earlier = signUp(event, 1);
        signedUpAt(later, "2026-01-02 10:00:00");
        signedUpAt(earlier, "2026-01-01 10:00:00");

        var moved = registrations.moveToQueue(List.of(later.id(), earlier.id()));

        assertEquals(2, moved.size());
        assertEquals(RegistrationStatus.QUEUED, statusOf(later));
        assertEquals(List.of(earlier.id(), later.id()), line(later, earlier));
    }

    @Test
    void onlySignUpsWaitingForADecisionGoOntoTheListAndOtherwiseNothingChanges() {
        var event = event(2, true);
        var pending = signUp(event, 0);
        var accepted = signUp(event, 1);
        registrations.accept(accepted.id(), false);

        var refusal = assertThrows(
                RefusalResponse.class, () -> registrations.moveToQueue(List.of(pending.id(), accepted.id())));

        assertEquals(EventRefusal.REGISTRATION_NOT_PENDING_FOR_QUEUE, refusal.refusal());
        assertEquals(RegistrationStatus.PENDING, statusOf(pending));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(accepted));
    }

    @Test
    void aRegistrationThatIsGoneIsNotFound() {
        var refusal = assertThrows(RefusalResponse.class, () -> registrations.moveToQueue(List.of(Integer.MAX_VALUE)));

        assertEquals(EventRefusal.EVENT_REGISTRATION_NOT_HERE_ON_STATUS_CHANGE, refusal.refusal());
    }

    @Test
    void whoeverRunsTheAppointmentReordersTheLine() {
        var event = event(1, false);
        signUp(event, 0);
        var first = signUp(event, 1);
        var second = signUp(event, 2);
        var third = signUp(event, 3);

        registrations.moveInQueue(third.id(), 1);
        assertEquals(List.of(third.id(), first.id(), second.id()), line(first, second, third));

        registrations.moveInQueue(third.id(), 10);
        assertEquals(List.of(first.id(), second.id(), third.id()), line(first, second, third));

        registrations.moveInQueue(second.id(), 0);
        assertEquals(List.of(second.id(), first.id(), third.id()), line(first, second, third));
    }

    @Test
    void onlyAWaitingSignUpHasAPlaceInLine() {
        var event = event(1, false);
        var accepted = signUp(event, 0);

        var refusal = assertThrows(RefusalResponse.class, () -> registrations.moveInQueue(accepted.id(), 1));

        assertEquals(EventRefusal.REGISTRATION_NOT_QUEUED, refusal.refusal());
    }

    @Test
    void movingUpByHandSkipsTheOrderButNotTheLimit() {
        var event = event(1, false);
        signUp(event, 0);
        var first = signUp(event, 1);
        var second = signUp(event, 2);

        var refusal = assertThrows(RefusalResponse.class, () -> registrations.accept(second.id(), false));
        assertEquals(EventRefusal.REGISTRATION_FULL, refusal.refusal());

        assertTrue(registrations.accept(second.id(), true));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(second));
        assertEquals(RegistrationStatus.QUEUED, statusOf(first));
        assertEquals(1, registrations.queueRanksOf(List.of(first.id())).get(first.id()));
    }

    @Test
    void anAppointmentWithoutLimitHasNoWaitingList() {
        var event = event(null, true);
        var pending = signUp(event, 0);

        var refusal = assertThrows(RefusalResponse.class, () -> registrations.moveToQueue(List.of(pending.id())));

        assertEquals(EventRefusal.QUEUE_WITHOUT_LIMIT, refusal.refusal());
        assertEquals(RegistrationStatus.PENDING, statusOf(pending));
    }

    @Test
    void aListKeptByFairnessTakesTheMovedInScoreOrder() {
        var event = event(1, true);
        query("UPDATE station_event SET queue_order = 'FAIRNESS' WHERE id = :id;")
                .single(call().bind("id", event.id()))
                .update();
        var past = event(null, false);
        query("UPDATE station_event SET start_time = now() - INTERVAL '20 days' WHERE id = :id;")
                .single(call().bind("id", past.id()))
                .update();
        eventRegistrationRepo.create(
                past.id(), members.get(1).id(), LocalDate.now().minusDays(20), RegistrationStatus.DENIED, null);
        var older = signUp(event, 0);
        var turnedDownBefore = signUp(event, 1);
        signedUpAt(older, "2026-01-01 10:00:00");
        signedUpAt(turnedDownBefore, "2026-01-02 10:00:00");

        registrations.moveToQueue(List.of(older.id(), turnedDownBefore.id()));

        assertEquals(List.of(turnedDownBefore.id(), older.id()), line(older, turnedDownBefore));
    }

    @Test
    void aPlaceAQuestionHoldsIsNotPutOntoTheList() {
        var event = event(1, true);
        var held = signUp(event, 0);
        query("UPDATE event_registration SET from_field = TRUE WHERE id = :id;")
                .single(call().bind("id", held.id()))
                .update();

        var refusal = assertThrows(RefusalResponse.class, () -> registrations.moveToQueue(List.of(held.id())));

        assertEquals(EventRefusal.REGISTRATION_HELD_BY_A_FIELD_ON_STATUS_CHANGE, refusal.refusal());
    }

    @Test
    void signUpsOfTwoAppointmentsJoinTheirOwnLists() {
        var one = event(1, true);
        var other = event(1, true);
        var first = signUp(one, 0);
        var second = signUp(other, 1);

        registrations.moveToQueue(List.of(second.id(), first.id()));

        var ranks = registrations.queueRanksOf(List.of(first.id(), second.id()));
        assertEquals(1, ranks.get(first.id()));
        assertEquals(1, ranks.get(second.id()));
    }

    @Test
    void aPartOfAnEventGroupIsLeftToItsEventGroup() {
        var groups = new EventGroupRepository();
        var group = groups.create(
                station.id(), new EventGroupDetails("Grundausbildung", null, null, null, true, null, null, null));
        var set = groups.createSet(group.id(), "Teil", 1, 0);
        var part = event(1, true);
        groups.claimPart(part.id(), set.id());
        var pending =
                eventRegistrationRepo.create(part.id(), members.get(0).id(), DATE, RegistrationStatus.PENDING, null);
        try {
            var toList = assertThrows(RefusalResponse.class, () -> registrations.moveToQueue(List.of(pending.id())));
            assertEquals(EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, toList.refusal());

            var inLine = assertThrows(RefusalResponse.class, () -> registrations.moveInQueue(pending.id(), 1));
            assertEquals(EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, inLine.refusal());
        } finally {
            eventRepo.delete(part.id());
            events.remove(part);
            groups.delete(group.id());
        }
    }
}
