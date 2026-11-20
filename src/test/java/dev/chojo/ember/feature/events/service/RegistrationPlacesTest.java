/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who gets a place when an appointment date has a limit. */
class RegistrationPlacesTest extends RepositoryTestBase {
    private static final LocalDate DATE = LocalDate.now().plusDays(14);

    private static final List<RegistrationStatus> announced = new ArrayList<>();
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
                new DomainEventBus(Set.of(new DomainEventHandler<EventRegistrationStatusChanged>() {
                    @Override
                    public Class<EventRegistrationStatusChanged> eventType() {
                        return EventRegistrationStatusChanged.class;
                    }

                    @Override
                    public void handle(EventRegistrationStatusChanged event) {
                        announced.add(event.newStatus());
                    }
                })),
                memberNameResolver,
                TestEventPlaces.of(eventRegistrationRepo));
        station = stationRepo.create("Registration Places Station");
        for (int i = 0; i < 4; i++) {
            var account = accountRepo.create("registration-places-" + i + "@test.com", "Platz", "Mitglied " + i);
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
        announced.clear();
        events.forEach(event -> eventRepo.delete(event.id()));
        events.clear();
    }

    private StationEvent event(@Nullable Integer limit, boolean requiresConfirmation) {
        var start = DATE.atTime(18, 0).toInstant(ZoneOffset.UTC);
        var event = eventRepo.create(
                station.id(),
                "Truppmann",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                true,
                RegistrationDeadline.none(),
                requiresConfirmation,
                null,
                limit,
                null,
                null);
        events.add(event);
        return event;
    }

    private EventRegistration signUp(StationEvent event, int member) {
        return registrations.register(event.id(), members.get(member).id(), DATE, !event.requiresConfirmation(), null);
    }

    private EventRegistration signUpFor(StationEvent event, int member, @Nullable WhenFull choice) {
        return registrations.register(
                event.id(),
                members.get(member).id(),
                DATE,
                !event.requiresConfirmation(),
                members.get(3).id(),
                true,
                choice);
    }

    private RegistrationStatus statusOf(EventRegistration registration) {
        return eventRegistrationRepo.findById(registration.id()).orElseThrow().status();
    }

    private Map<Integer, Integer> ranks(EventRegistration... waiting) {
        return registrations.queueRanksOf(
                Arrays.stream(waiting).map(EventRegistration::id).toList());
    }

    @Test
    void theLastPlaceIsTakenAndTheNextOneWaitsInLine() {
        var event = event(1, false);

        var first = signUp(event, 0);
        var second = signUp(event, 1);
        var third = signUp(event, 2);

        assertEquals(RegistrationStatus.ACCEPTED, first.status());
        assertEquals(RegistrationStatus.QUEUED, second.status());
        assertEquals(RegistrationStatus.QUEUED, third.status());
        assertEquals(Map.of(second.id(), 1, third.id(), 2), ranks(first, second, third));
    }

    @Test
    void aFreePlaceGoesToWhoeverWaitsBeforeANewcomer() {
        var event = event(1, false);
        signUp(event, 0);
        var waiting = signUp(event, 1);
        query("UPDATE station_event SET registration_limit = 2 WHERE id = :id;")
                .single(call().bind("id", event.id()))
                .update();

        var newcomer = signUp(event, 2);

        assertEquals(RegistrationStatus.QUEUED, newcomer.status(), "nobody overtakes the waiting list");
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(waiting));
        assertEquals(Map.of(newcomer.id(), 1), ranks(waiting, newcomer));
    }

    @Test
    void signingUpAgainKeepsThePlaceInLine() {
        var event = event(1, false);
        signUp(event, 0);
        var waiting = signUp(event, 1);
        var later = signUp(event, 2);

        var again = signUp(event, 1);

        assertEquals(waiting.id(), again.id());
        assertEquals(RegistrationStatus.QUEUED, again.status());
        assertEquals(Map.of(waiting.id(), 1, later.id(), 2), ranks(waiting, later));
    }

    @Test
    void twoSignUpsAtOnceNeverShareTheLastPlace() throws Exception {
        var event = event(1, false);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<EventRegistration>> sent = new ArrayList<>();
            for (int member = 0; member < 2; member++) {
                int who = member;
                sent.add(pool.submit(() -> {
                    start.await();
                    return signUp(event, who);
                }));
            }
            start.countDown();
            var statuses = new ArrayList<RegistrationStatus>();
            for (var future : sent) statuses.add(future.get().status());

            assertTrue(statuses.contains(RegistrationStatus.ACCEPTED));
            assertTrue(statuses.contains(RegistrationStatus.QUEUED));
        }
    }

    @Test
    void whoeverRunsTheAppointmentChoosesOnAFullDate() {
        var event = event(1, false);
        signUp(event, 0);

        var refusal = assertThrows(RefusalResponse.class, () -> signUpFor(event, 1, null));
        assertEquals(EventRefusal.REGISTRATION_FULL, refusal.refusal());
        assertEquals(RefusalDetail.count(1), refusal.detail(), "the refusal names the places");

        assertEquals(
                RegistrationStatus.ACCEPTED,
                signUpFor(event, 1, WhenFull.OVER_LIMIT).status());
        assertEquals(
                RegistrationStatus.QUEUED, signUpFor(event, 2, WhenFull.QUEUE).status());
    }

    @Test
    void anAppointmentThatConfirmsKeepsEverybodyWaitingForADecision() {
        var event = event(1, true);
        signUp(event, 0);

        assertEquals(RegistrationStatus.PENDING, signUp(event, 1).status());
        assertEquals(RegistrationStatus.PENDING, signUp(event, 2).status());
    }

    @Test
    void acceptingBeyondTheLimitNeedsToBeSaid() {
        var event = event(1, true);
        var first = signUp(event, 0);
        var second = signUp(event, 1);
        assertTrue(registrations.accept(first.id(), false));

        var refusal = assertThrows(RefusalResponse.class, () -> registrations.accept(second.id(), false));
        assertEquals(EventRefusal.REGISTRATION_FULL, refusal.refusal());
        assertEquals(RegistrationStatus.PENDING, statusOf(second));

        assertTrue(registrations.accept(second.id(), true));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(second));
        assertTrue(registrations.accept(second.id(), false), "accepting the accepted again needs no room");
    }

    @Test
    void comingAfterAllTakesAFreePlaceOrWaitsInLine() {
        var event = event(1, false);
        var declined =
                eventRegistrationRepo.create(event.id(), members.get(0).id(), DATE, RegistrationStatus.DECLINED, null);
        var otherDeclined =
                eventRegistrationRepo.create(event.id(), members.get(1).id(), DATE, RegistrationStatus.DECLINED, null);

        assertTrue(registrations.answerComing(declined.id(), false, null));
        assertTrue(registrations.answerComing(otherDeclined.id(), false, null));

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(declined));
        assertEquals(RegistrationStatus.QUEUED, statusOf(otherDeclined));
        assertEquals(Map.of(otherDeclined.id(), 1), ranks(otherDeclined));
        assertTrue(registrations.answerComing(otherDeclined.id(), false, null), "waiting already, nothing moves");
        assertEquals(Map.of(otherDeclined.id(), 1), ranks(otherDeclined));
    }

    @Test
    void anAnswerForARegistrationThatIsGoneChangesNothing() {
        assertTrue(!registrations.answerComing(Integer.MAX_VALUE, false, null));
        assertTrue(!registrations.accept(Integer.MAX_VALUE, false));
    }

    @Test
    void anAppointmentWithoutLimitTakesEverybody() {
        var event = event(null, false);

        assertEquals(RegistrationStatus.ACCEPTED, signUp(event, 0).status());
        assertEquals(RegistrationStatus.ACCEPTED, signUp(event, 1).status());
        assertNull(ranks(signUp(event, 1)).get(0));
    }

    @Test
    void aDateListNeedsItsDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RegistrationQueueRepository.QueueList(RegistrationQueueRepository.QueueKind.DATE, 1, null));
    }

    @Test
    void aMemberCannotChooseToGoBeyondTheLimit() {
        var event = event(1, false);
        signUp(event, 0);

        var own = registrations.register(event.id(), members.get(1).id(), DATE, true, null, false, WhenFull.OVER_LIMIT);

        assertEquals(RegistrationStatus.QUEUED, own.status());
    }

    @Test
    void whoeverRunsTheAppointmentAnswersComingWithTheirChoice() {
        var event = event(1, false);
        signUp(event, 0);
        var first =
                eventRegistrationRepo.create(event.id(), members.get(1).id(), DATE, RegistrationStatus.DECLINED, null);
        var second =
                eventRegistrationRepo.create(event.id(), members.get(2).id(), DATE, RegistrationStatus.DECLINED, null);

        var refusal = assertThrows(RefusalResponse.class, () -> registrations.answerComing(first.id(), true, null));
        assertEquals(EventRefusal.REGISTRATION_FULL, refusal.refusal());
        assertEquals(RegistrationStatus.DECLINED, statusOf(first));

        assertTrue(registrations.answerComing(first.id(), true, WhenFull.OVER_LIMIT));
        assertTrue(registrations.answerComing(second.id(), true, WhenFull.QUEUE));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(first));
        assertEquals(RegistrationStatus.QUEUED, statusOf(second));
    }

    @Test
    void comingAgainOnAnAppointmentThatConfirmsTellsWhoeverDecides() {
        var event = event(1, true);
        var declined =
                eventRegistrationRepo.create(event.id(), members.get(0).id(), DATE, RegistrationStatus.DECLINED, null);

        assertTrue(registrations.answerComing(declined.id(), false, null));

        assertEquals(RegistrationStatus.PENDING, statusOf(declined));
        assertEquals(List.of(RegistrationStatus.PENDING), announced);
    }

    @Test
    void signingUpForAPlaceAQuestionHoldsMakesItTheMembersOwn() {
        var event = event(1, false);
        var held =
                eventRegistrationRepo.create(event.id(), members.get(0).id(), DATE, RegistrationStatus.ACCEPTED, null);
        query("UPDATE event_registration SET from_field = TRUE WHERE id = :id;")
                .single(call().bind("id", held.id()))
                .update();

        var own = signUp(event, 0);

        assertEquals(held.id(), own.id());
        assertEquals(RegistrationStatus.ACCEPTED, own.status());
        assertFalse(own.fromField(), "the question letting go no longer takes the place");
    }

    @Test
    void aSignUpLeftWaitingForADecisionIsDecidedAnewOnceTheAppointmentNoLongerConfirms() {
        var event = event(2, false);
        var pending =
                eventRegistrationRepo.create(event.id(), members.get(0).id(), DATE, RegistrationStatus.PENDING, null);

        var again = signUp(event, 0);

        assertEquals(pending.id(), again.id());
        assertEquals(RegistrationStatus.ACCEPTED, again.status());
    }
}
