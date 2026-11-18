/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventChanged;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.handler.EventLimitRaisedHandler;
import dev.chojo.ember.feature.events.handler.RegistrationPlacesFreedHandler;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestEventPlaces;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who moves up from the waiting list when a place falls free. */
class QueueMoveUpTest extends RepositoryTestBase {
    private static final LocalDate DATE = LocalDate.now().plusDays(7);

    private static final List<RegistrationStatusChangedRecord> announced = new ArrayList<>();
    private static final List<EventRegistrationStatusChanged> told = new ArrayList<>();
    private static EventRegistrationService registrations;
    private static Station station;
    private static final List<Account> accounts = new ArrayList<>();
    private static final List<StationMember> members = new ArrayList<>();
    private final List<StationEvent> events = new ArrayList<>();
    private final List<Station> partnerStations = new ArrayList<>();

    private record RegistrationStatusChangedRecord(int memberId, RegistrationStatus status) {}

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
                        announced.add(new RegistrationStatusChangedRecord(event.memberId(), event.newStatus()));
                        told.add(event);
                    }
                })),
                memberNameResolver,
                TestEventPlaces.of(eventRegistrationRepo));
        station = stationRepo.create("Queue Move Up Station");
        for (int i = 0; i < 4; i++) {
            var account = accountRepo.create("queue-move-up-" + i + "@test.com", "Nach", "Rücker " + i);
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
        partnerStations.forEach(partner -> stationRepo.delete(partner.id()));
        partnerStations.clear();
        announced.clear();
        told.clear();
    }

    private StationEvent event(@Nullable Integer limit, LocalDate date, StationEvent.EventType type) {
        var start = date.atTime(18, 0).toInstant(ZoneOffset.UTC);
        var event = eventRepo.create(
                station.id(),
                "Sprechfunk",
                "desc",
                type,
                type == StationEvent.EventType.ONE_TIME
                        ? null
                        : date.getDayOfWeek().getValue(),
                start,
                start.plusSeconds(7200),
                null,
                true,
                null,
                false,
                null,
                limit,
                null,
                null,
                null);
        events.add(event);
        return event;
    }

    private StationEvent full(int waiting) {
        var event = event(1, DATE, StationEvent.EventType.ONE_TIME);
        signUp(event, 0, DATE);
        for (int member = 1; member <= waiting; member++) signUp(event, member, DATE);
        return event;
    }

    private EventRegistration signUp(StationEvent event, int member, LocalDate date) {
        return registrations.register(event.id(), members.get(member).id(), date, true, null);
    }

    private EventRegistration registrationOf(StationEvent event, int member, LocalDate date) {
        return eventRegistrationRepo
                .findOne(event.id(), members.get(member).id(), date)
                .orElseThrow();
    }

    private RegistrationStatus statusOf(StationEvent event, int member) {
        return registrationOf(event, member, DATE).status();
    }

    private boolean toldMovedUp(int member) {
        return announced.contains(
                new RegistrationStatusChangedRecord(members.get(member).id(), RegistrationStatus.ACCEPTED));
    }

    @Test
    void givingUpAPlaceLetsTheFirstInLineMoveUp() {
        var event = full(2);

        registrations.withdraw(registrationOf(event, 0, DATE).id());

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 1));
        assertEquals(RegistrationStatus.QUEUED, statusOf(event, 2));
        assertTrue(toldMovedUp(1));
        assertEquals(
                1,
                registrations
                        .queueRanksOf(List.of(registrationOf(event, 2, DATE).id()))
                        .values()
                        .iterator()
                        .next());
    }

    @Test
    void sayingNoToAHeldPlaceLetsTheFirstInLineMoveUp() {
        var event = full(1);

        registrations.refuse(registrationOf(event, 0, DATE).id());

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 1));
    }

    @Test
    void decliningAHeldPlaceLetsTheFirstInLineMoveUp() {
        var event = full(1);

        registrations.decline(event.id(), members.get(0).id(), DATE, null);

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 1));
    }

    @Test
    void turningDownAnAcceptedMemberLetsTheFirstInLineMoveUp() {
        var event = full(1);

        registrations.updateStatus(registrationOf(event, 0, DATE).id(), RegistrationStatus.DENIED);

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 1));
    }

    @Test
    void leavingTheWaitingListFreesNoPlace() {
        var event = full(2);

        registrations.withdraw(registrationOf(event, 1, DATE).id());

        assertEquals(RegistrationStatus.QUEUED, statusOf(event, 2));
        assertEquals(
                1,
                registrations
                        .queueRanksOf(List.of(registrationOf(event, 2, DATE).id()))
                        .values()
                        .iterator()
                        .next());
    }

    @Test
    void aRaisedLimitMovesUpAsManyAsNowFitAndALoweredOneTakesNobodysPlace() {
        var event = full(3);
        var handler = new EventLimitRaisedHandler(() -> registrations, occurrenceCalendar);
        var raised = limited(event, 3);

        handler.handle(new EventChanged(station.id(), event, raised));

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 1));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 2));
        assertEquals(RegistrationStatus.QUEUED, statusOf(event, 3));

        var lowered = limited(event, 1);
        handler.handle(new EventChanged(station.id(), raised, lowered));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 2), "a lowered limit removes nobody");
    }

    @Test
    void removingTheLimitLetsEverybodyIn() {
        var event = full(2);
        var handler = new EventLimitRaisedHandler(() -> registrations, occurrenceCalendar);

        handler.handle(new EventChanged(station.id(), event, limited(event, null)));

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 2));
    }

    private StationEvent limited(StationEvent event, @Nullable Integer limit) {
        query("UPDATE station_event SET registration_limit = :limit WHERE id = :id;")
                .single(call().bind("limit", limit).bind("id", event.id()))
                .update();
        return eventRepo.findById(event.id()).orElseThrow();
    }

    @Test
    void theDatesOfASeriesKeepTheirOwnListsApart() {
        var series = event(1, DATE, StationEvent.EventType.RECURRING);
        var nextWeek = DATE.plusWeeks(1);
        signUp(series, 0, DATE);
        signUp(series, 1, DATE);
        signUp(series, 2, nextWeek);
        signUp(series, 3, nextWeek);

        registrations.withdraw(registrationOf(series, 2, nextWeek).id());

        assertEquals(RegistrationStatus.QUEUED, registrationOf(series, 1, DATE).status());
        assertEquals(
                RegistrationStatus.ACCEPTED, registrationOf(series, 3, nextWeek).status());
    }

    @Test
    void nobodyMovesUpOnceTheDateStarted() {
        var past = LocalDate.now().minusDays(1);
        var event = event(1, past, StationEvent.EventType.ONE_TIME);
        eventRegistrationRepo.create(event.id(), members.get(1).id(), past, RegistrationStatus.QUEUED, null);
        query("""
                INSERT INTO registration_queue(event_id, event_date, registration_id, position)
                VALUES (:event_id, :date, :registration_id, 1);""")
                .single(call().bind("event_id", event.id())
                        .bind("date", past)
                        .bind("registration_id", registrationOf(event, 1, past).id()))
                .insert();

        assertTrue(registrations.moveUp(event.id(), past).isEmpty());
        assertEquals(RegistrationStatus.QUEUED, registrationOf(event, 1, past).status());
    }

    @Test
    void takingBackAGivenUpSeatThatSomebodyMovedIntoGoesToTheFrontOfTheLine() {
        var event = full(2);
        var given = registrationOf(event, 0, DATE);
        registrations.withdraw(given.id());

        registrations.undoWithdrawal(given.id());

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 1), "whoever moved up keeps the seat");
        assertEquals(RegistrationStatus.QUEUED, statusOf(event, 0));
        var ranks = registrations.queueRanksOf(
                List.of(given.id(), registrationOf(event, 2, DATE).id()));
        assertEquals(1, ranks.get(given.id()));
        assertEquals(2, ranks.get(registrationOf(event, 2, DATE).id()));
    }

    @Test
    void takingBackAGivenUpSeatThatIsStillFreeGetsTheSeat() {
        var event = event(1, DATE, StationEvent.EventType.ONE_TIME);
        var held = signUp(event, 0, DATE);
        registrations.withdraw(held.id());

        registrations.undoWithdrawal(held.id());

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 0));
    }

    @Test
    void takingBackLeavingTheWaitingListPutsTheSignUpBackInLine() {
        var event = full(2);
        var left = registrationOf(event, 1, DATE);
        registrations.withdraw(left.id());

        registrations.undoWithdrawal(left.id());

        assertEquals(RegistrationStatus.QUEUED, statusOf(event, 1));
        assertEquals(2, registrations.queueRanksOf(List.of(left.id())).get(left.id()));
    }

    @Test
    void aPartnersMemberInLineMovesUpInTurn() {
        var event = event(1, DATE, StationEvent.EventType.ONE_TIME);
        signUp(event, 0, DATE);
        int partnerRegistration = partnerWaiting(event);
        signUp(event, 1, DATE);

        registrations.withdraw(registrationOf(event, 0, DATE).id());

        assertEquals(RegistrationStatus.QUEUED, statusOf(event, 1), "the partner's member was first");
        assertEquals(
                "ACCEPTED",
                query("SELECT status FROM event_federation_registration WHERE id = :id;")
                        .single(call().bind("id", partnerRegistration))
                        .map(row -> row.getString("status"))
                        .first()
                        .orElseThrow());
    }

    private int partnerWaiting(StationEvent event) {
        var partnerStation = stationRepo.create("Move Up Partner " + UUID.randomUUID());
        partnerStations.add(partnerStation);
        int partnerId = query("""
                        INSERT INTO federation_partner (station_id, partner_station_id, status, remote_host, cluster_managed)
                        VALUES (:station, :partner::UUID, 'ACTIVE', NULL, FALSE)
                        RETURNING id;""")
                .single(call().bind("station", station.id())
                        .bind("partner", partnerStation.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        int registration = query("""
                        INSERT INTO event_federation_registration(event_id, partner_id, remote_member_id, event_date, status)
                        VALUES (:event_id, :partner_id, gen_random_uuid(), :event_date, 'QUEUED')
                        RETURNING id;""")
                .single(call().bind("event_id", event.id())
                        .bind("partner_id", partnerId)
                        .bind("event_date", DATE))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        var list = RegistrationQueueRepository.ofDate(event.id(), DATE);
        new RegistrationQueueRepository()
                .insertAt(list, RegistrationQueueRepository.QueueOwner.ofPartner(registration), 1, 50.0);
        return registration;
    }

    private void waitsBySql(StationEvent event, int member, RegistrationStatus status, int position) {
        var registration =
                eventRegistrationRepo.create(event.id(), members.get(member).id(), DATE, status, null);
        new RegistrationQueueRepository()
                .insertAt(
                        RegistrationQueueRepository.ofDate(event.id(), DATE),
                        RegistrationQueueRepository.QueueOwner.local(registration.id()),
                        position,
                        50.0);
    }

    @Test
    void aQuestionLettingGoOfAPlaceFillsItFromTheLine() {
        var event = event(1, DATE, StationEvent.EventType.ONE_TIME);
        var fields = new EventFieldRegistrationService(
                eventRepo,
                eventFieldRepo,
                eventRegistrationRepo,
                occurrenceCalendar,
                new DomainEventBus(Set.of(new RegistrationPlacesFreedHandler(() -> registrations))),
                memberNameResolver);
        var field = eventFieldRepo.create(
                event.id(),
                "Fahrer",
                FieldType.MEMBER_LIST,
                new EventFieldConfig(null, null, null, null, true, null, false).settings(),
                QuestionValues.formatMembers(List.of(members.get(0).id())),
                0,
                false,
                null,
                false);
        fields.reconcile(event.id());
        var day = registrationOf(event, 0, eventDay(event)).eventDate();
        registrations.register(event.id(), members.get(1).id(), day, true, null);
        assertEquals(RegistrationStatus.QUEUED, registrationOf(event, 1, day).status());

        eventFieldRepo.updateValue(field.id(), "");
        fields.reconcile(event.id());

        assertEquals(RegistrationStatus.ACCEPTED, registrationOf(event, 1, day).status());
    }

    private LocalDate eventDay(StationEvent event) {
        return eventRegistrationRepo.findByEvent(event.id()).getFirst().eventDate();
    }

    @Test
    void aPlaceFreedUnderALoweredLimitMovesNobodyUpWhileTheDateIsStillFull() {
        var event = event(3, DATE, StationEvent.EventType.ONE_TIME);
        signUp(event, 0, DATE);
        signUp(event, 1, DATE);
        signUp(event, 2, DATE);
        limited(event, 1);
        signUp(event, 3, DATE);

        registrations.withdraw(registrationOf(event, 0, DATE).id());

        assertEquals(RegistrationStatus.QUEUED, statusOf(event, 3), "two accepted against one place is still full");
    }

    @Test
    void anEntryWhoseSignUpNoLongerWaitsLeavesTheLineWithoutAPlace() {
        var event = event(1, DATE, StationEvent.EventType.ONE_TIME);
        signUp(event, 0, DATE);
        waitsBySql(event, 1, RegistrationStatus.DECLINED, 1);
        waitsBySql(event, 2, RegistrationStatus.QUEUED, 2);

        registrations.withdraw(registrationOf(event, 0, DATE).id());

        assertEquals(RegistrationStatus.DECLINED, statusOf(event, 1));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 2));
    }

    @Test
    void aCalledOffDateMovesNobodyUp() {
        var event = full(1);
        eventDateCancellationRepo.cancel(event.id(), DATE, CancellationCause.MANUAL, null, null);

        registrations.withdraw(registrationOf(event, 0, DATE).id());

        assertEquals(RegistrationStatus.QUEUED, statusOf(event, 1));
    }

    @Test
    void takingBackLeavingTheLineTakesAPlaceThatFellFreeMeanwhile() {
        var event = full(1);
        var left = registrationOf(event, 1, DATE);
        registrations.withdraw(left.id());
        registrations.withdraw(registrationOf(event, 0, DATE).id());

        registrations.undoWithdrawal(left.id());

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, 1));
        assertTrue(toldMovedUp(1));
    }

    @Test
    void aSeatTakenBackStaysFirstInLineOnAListKeptByFairness() {
        var event = full(1);
        query("UPDATE station_event SET queue_order = 'FAIRNESS' WHERE id = :id;")
                .single(call().bind("id", event.id()))
                .update();
        var given = registrationOf(event, 0, DATE);
        registrations.withdraw(given.id());
        registrations.undoWithdrawal(given.id());
        var past = event(null, LocalDate.now().minusDays(30), StationEvent.EventType.ONE_TIME);
        eventRegistrationRepo.create(
                past.id(), members.get(2).id(), LocalDate.now().minusDays(30), RegistrationStatus.DENIED, null);

        var newcomer = signUp(event, 2, DATE);

        var ranks = registrations.queueRanksOf(List.of(given.id(), newcomer.id()));
        assertEquals(1, ranks.get(given.id()), "nobody is placed before a seat taken back");
        assertEquals(2, ranks.get(newcomer.id()));
    }

    private EventRegistrationStatusChanged toldTo(int member) {
        return told.stream()
                .filter(event -> event.memberId() == members.get(member).id())
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    @Test
    void whoeverMovesUpIsToldSo() {
        var event = full(1);

        registrations.withdraw(registrationOf(event, 0, DATE).id());

        var notice = toldTo(1);
        assertTrue(notice.movedUp());
        assertTrue(!notice.shortNotice(), "a week ahead is no short notice");
    }

    @Test
    void movingUpLessThanADayBeforeTheStartSaysSo() {
        var start = Instant.now().plus(3, ChronoUnit.HOURS);
        var day = start.atZone(occurrenceCalendar.zoneOf(station.id())).toLocalDate();
        var event = eventRepo.create(
                station.id(),
                "Kurzfristig",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(3600),
                null,
                true,
                null,
                false,
                null,
                1,
                null,
                null,
                null);
        events.add(event);
        signUp(event, 0, day);
        signUp(event, 1, day);

        registrations.withdraw(registrationOf(event, 0, day).id());

        assertTrue(toldTo(1).shortNotice());
    }

    @Test
    void somebodyPutOnTheListByWhoeverRunsTheAppointmentIsToldTheirPlace() {
        var event = full(1);
        registrations.register(
                event.id(), members.get(2).id(), DATE, true, members.get(0).id(), true, WhenFull.QUEUE);

        var notice = toldTo(2);
        assertEquals(RegistrationStatus.QUEUED, notice.newStatus());
        assertEquals(2, notice.queuePosition());
    }

    @Test
    void pendingSignUpsPutOnTheListAreToldTheirPlaceInSignUpOrderAndOnlyOnce() {
        var start = DATE.atTime(18, 0).toInstant(ZoneOffset.UTC);
        var event = eventRepo.create(
                station.id(),
                "Mit Entscheidung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                true,
                null,
                true,
                null,
                1,
                null,
                null,
                null);
        events.add(event);
        var first = signUp(event, 0, DATE);
        var second = signUp(event, 1, DATE);

        registrations.moveToQueue(List.of(second.id(), first.id()));

        assertEquals(1, toldTo(0).queuePosition());
        assertEquals(2, toldTo(1).queuePosition());

        told.clear();
        registrations.register(
                event.id(), members.get(0).id(), DATE, false, members.get(3).id(), true, WhenFull.QUEUE);
        assertTrue(told.isEmpty(), "signing up somebody who already waits tells them nothing new");
    }

    @Test
    void comingAfterAllOntoTheListByWhoeverRunsTheAppointmentCarriesThePlace() {
        var event = full(1);
        var declined =
                eventRegistrationRepo.create(event.id(), members.get(2).id(), DATE, RegistrationStatus.DECLINED, null);

        registrations.answerComing(declined.id(), true, WhenFull.QUEUE);

        assertEquals(RegistrationStatus.QUEUED, toldTo(2).newStatus());
        assertEquals(2, toldTo(2).queuePosition());
    }
}
