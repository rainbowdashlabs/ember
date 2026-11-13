/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventGroupCancelled;
import dev.chojo.ember.event.events.EventGroupDeadlineExpired;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.handler.EventCancelledHandler;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What people hear about an event group: once for the event group when it is called off or its deadline comes
 * and goes, never once for every part.
 */
class EventGroupNoticesTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventGroupRepository eventGroupRepo = new EventGroupRepository();
    private EventGroupService eventGroups;
    private EventGroupRegistrationService signUps;
    private Notifier notifier;
    private DomainEventBus announcements;
    private ZoneId zone;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event group Notice Station");
        anna = memberOf("anna@notices.test", "Anna");
        ben = memberOf("ben@notices.test", "Ben");
    }

    private static StationMember memberOf(String email, String firstName) {
        var account = accountRepo.create(email, firstName, "Notice");
        accounts.add(account.id());
        return stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        notifier = mock(Notifier.class);
        announcements = mock(DomainEventBus.class);
        eventGroups = new EventGroupService(
                eventGroupRepo,
                eventRepo,
                restrictionService,
                occurrenceCalendar,
                new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility),
                new EventFieldRepository());
        var registrations = newEventServices(new DomainEventBus(Set.of())).registration();
        signUps = new EventGroupRegistrationService(
                eventGroupRepo,
                new EventGroupRegistrationRepository(),
                eventRepo,
                eventRegistrationRepo,
                registrations,
                occurrenceCalendar,
                mock(DomainEventBus.class),
                memberNameResolver,
                new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility),
                new EventGroupPartnerRepository());
        zone = occurrenceCalendar.zoneOf(station.id());
    }

    private EventGroup eventGroup(Instant deadline, boolean confirm) {
        return eventGroups.create(
                station.id(),
                new EventGroupDetails("Jugendflamme 1", null, null, deadline, confirm, null),
                List.of(new EventGroupSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
    }

    private StationEvent part(EventGroup eventGroup, int daysAhead) {
        var start = LocalDate.now(zone)
                .plusDays(daysAhead)
                .atTime(9, 0)
                .atZone(zone)
                .toInstant();
        var event = eventRepo.create(
                station.id(),
                "Tag " + daysAhead,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(8)),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventGroups.addPart(
                station.id(),
                eventGroup.id(),
                event.id(),
                eventGroups.findSets(eventGroup.id()).getFirst().id());
        return event;
    }

    @Test
    void callingAnEventGroupOffCallsOffItsPartsToComeAndTellsItsPeopleOnce() {
        var eventGroup = eventGroup(null, false);
        var past = part(eventGroup, -2);
        var first = part(eventGroup, 10);
        var second = part(eventGroup, 11);
        signUps.register(
                station.id(), eventGroup.id(), anna.id(), Set.of(first.id(), second.id()), Map.of(), null, false);
        var bus = new DomainEventBus(
                Set.of(new EventCancelledHandler(notifier, eventRegistrationRepo, occurrenceCalendar, eventGroupRepo)));
        var cancellation = new EventGroupCancellationService(
                eventGroupRepo,
                new EventGroupRegistrationRepository(),
                eventRepo,
                newEventServices(bus).cancellation(),
                occurrenceCalendar,
                announcements);

        cancellation.cancel(station.id(), eventGroup.id(), "Zu wenige Ausbilder", null);

        var calendar = occurrenceCalendar.forStation(station.id());
        assertTrue(calendar.cancelledAltogether(eventRepo.findById(first.id()).orElseThrow()));
        assertTrue(calendar.cancelledAltogether(eventRepo.findById(second.id()).orElseThrow()));
        assertFalse(calendar.cancelledAltogether(eventRepo.findById(past.id()).orElseThrow()));
        verify(notifier, never()).notify(any(), any(), any(), any());
        var announced = ArgumentCaptor.forClass(DomainEvent.class);
        verify(announcements).publish(announced.capture());
        var cancelled = (EventGroupCancelled) announced.getValue();
        assertEquals(List.of(anna.id()), cancelled.memberIds());
        assertEquals("Zu wenige Ausbilder", cancelled.reason());
        assertTrue(eventGroupRepo.findById(eventGroup.id()).orElseThrow().cancelled());

        var again = assertThrows(
                RefusalResponse.class, () -> cancellation.cancel(station.id(), eventGroup.id(), null, null));
        assertEquals(EventRefusal.EVENT_GROUP_CANCELLED, again.refusal());
    }

    @Test
    void aPassedDeadlineWithSignUpsWaitingIsToldOnceForTheEventGroup() {
        var eventGroup = eventGroup(Instant.now().plus(Duration.ofDays(2)), true);
        var day = part(eventGroup, 12);
        signUps.register(station.id(), eventGroup.id(), anna.id(), Set.of(day.id()), Map.of(), null, false);
        query("UPDATE event_group SET registration_deadline = now() - INTERVAL '1 minute' WHERE id = :id;")
                .single(call().bind("id", eventGroup.id()))
                .update();
        var checker = checker();

        checker.check();
        checker.check();

        var announced = ArgumentCaptor.forClass(DomainEvent.class);
        verify(announcements, atLeastOnce()).publish(announced.capture());
        var expired = announced.getAllValues().stream()
                .filter(event -> event instanceof EventGroupDeadlineExpired)
                .map(event -> (EventGroupDeadlineExpired) event)
                .filter(event -> event.eventGroupId() == eventGroup.id())
                .toList();
        assertEquals(1, expired.size());
        assertEquals(1, expired.getFirst().pendingCount());
        assertTrue(eventRepo.findOneTimeEventsWithExpiredDeadline().stream()
                .noneMatch(entry -> entry.eventId() == day.id()));
    }

    @Test
    void membersWhoNeverAnsweredAreWarnedOnceBeforeTheEventGroupCloses() {
        var eventGroup = eventGroup(Instant.now().plus(Duration.ofHours(20)), false);
        var day = part(eventGroup, 13);
        signUps.register(station.id(), eventGroup.id(), anna.id(), Set.of(day.id()), Map.of(), null, false);
        var checker = checker();

        checker.check();
        checker.check();

        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier, times(1)).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), data.capture(), any());
        assertEquals("event-group", data.getValue().link().route());
        assertTrue(eventRepo.findEventsClosingIn(1).stream().noneMatch(entry -> entry.eventId() == day.id()));
    }

    private EventGroupCancellationService cancellationOver(EventCancellationService dates) {
        return new EventGroupCancellationService(
                eventGroupRepo,
                new EventGroupRegistrationRepository(),
                eventRepo,
                dates,
                occurrenceCalendar,
                announcements);
    }

    @Test
    void aPartAlreadyOffIsPassedOverAndTheEventGroupStillCalledOffAndAnnounced() {
        var eventGroup = eventGroup(null, false);
        var first = part(eventGroup, 14);
        var second = part(eventGroup, 15);
        var dates = newEventServices(new DomainEventBus(Set.of())).cancellation();
        dates.cancelDate(first, first.startTime().atZone(zone).toLocalDate(), "Krank", null);

        cancellationOver(dates).cancel(station.id(), eventGroup.id(), null, null);

        assertTrue(occurrenceCalendar
                .forStation(station.id())
                .cancelledAltogether(eventRepo.findById(second.id()).orElseThrow()));
        verify(announcements).publish(any(EventGroupCancelled.class));
    }

    @Test
    void aCalledOffEventGroupStaysOffPartByPartAndTakesNoNewParts() {
        var eventGroup = eventGroup(null, false);
        var first = part(eventGroup, 16);
        var dates = newEventServices(new DomainEventBus(Set.of())).cancellation();
        cancellationOver(dates).cancel(station.id(), eventGroup.id(), null, null);
        var date = first.startTime().atZone(zone).toLocalDate();
        var stored = eventRepo.findById(first.id()).orElseThrow();

        var restore = assertThrows(RefusalResponse.class, () -> dates.restoreDate(stored, date));
        assertEquals(EventRefusal.EVENT_GROUP_PART_STAYS_CANCELLED, restore.refusal());
        var another = eventRepo.create(
                station.id(),
                "Nachzügler",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                first.startTime(),
                first.endTime(),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var add = assertThrows(
                RefusalResponse.class,
                () -> eventGroups.addPart(
                        station.id(),
                        eventGroup.id(),
                        another.id(),
                        eventGroups.findSets(eventGroup.id()).getFirst().id()));
        assertEquals(EventRefusal.EVENT_GROUP_CANCELLED, add.refusal());
    }

    @Test
    void movingTheDeadlineWarnsAgainAndAnEventGroupWithNothingLeftWarnsNobody() {
        var eventGroup = eventGroup(Instant.now().plus(Duration.ofHours(20)), false);
        part(eventGroup, 17);
        var checker = checker();
        checker.check();
        verify(notifier, atLeastOnce()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());

        eventGroups.update(
                station.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        "Jugendflamme 1", null, null, Instant.now().plus(Duration.ofHours(30)), false, null));
        var later = mock(Notifier.class);
        notifier = later;
        checker().check();
        verify(later, atLeastOnce()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());

        var empty = eventGroup(Instant.now().plus(Duration.ofHours(20)), false);
        var quiet = mock(Notifier.class);
        notifier = quiet;
        checker().check();
        assertTrue(eventGroupRepo.findClosingIn(1).stream().noneMatch(entry -> entry.eventGroupId() == empty.id()));
        verify(quiet, never()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());
    }

    @Test
    void onlyMembersTheEventGroupIsMeantForWhoNeverAnsweredAreWarned() {
        var eventGroup = eventGroup(Instant.now().plus(Duration.ofHours(20)), false);
        var day = part(eventGroup, 18);
        var former = memberOf("former@notices.test", "Fritz");
        query("UPDATE station_member SET former = TRUE WHERE id = :id;")
                .single(call().bind("id", former.id()))
                .update();
        signUps.register(station.id(), eventGroup.id(), anna.id(), Set.of(day.id()), Map.of(), null, false);
        signUps.withdraw(station.id(), eventGroup.id(), anna.id());
        eventGroups.setAudiences(
                station.id(),
                eventGroup.id(),
                RestrictionAudience.empty(),
                new RestrictionAudience(List.of(), List.of(), List.of(), List.of(anna.id()), RestrictionMode.AND));

        assertEquals(
                List.of(),
                eventGroupRepo.findUnansweredMemberIds(eventGroup.id(), station.id()).stream()
                        .filter(id -> id == anna.id() || id == former.id())
                        .toList());
        checker().check();

        verify(notifier, never()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());
    }

    @Test
    void theListOfAnswersStillOwedLeavesPartsOut() {
        var eventGroup = eventGroup(Instant.now().plus(Duration.ofDays(5)), false);
        var day = part(eventGroup, 19);

        assertTrue(eventRegistrationRepo.findAwaitingAnswer(List.of(ben.id())).stream()
                .noneMatch(entry -> entry.eventId() == day.id()));
    }

    @Test
    void aSinglePartCalledOffOnALiveEventGroupStillTellsItsPeople() {
        var eventGroup = eventGroup(null, false);
        var day = part(eventGroup, 20);
        signUps.register(station.id(), eventGroup.id(), ben.id(), Set.of(day.id()), Map.of(), null, false);
        var bus = new DomainEventBus(
                Set.of(new EventCancelledHandler(notifier, eventRegistrationRepo, occurrenceCalendar, eventGroupRepo)));

        newEventServices(bus)
                .cancellation()
                .cancelDate(day, day.startTime().atZone(zone).toLocalDate(), null, null);

        verify(notifier).notify(any(), eq(NotificationType.EVENT_CANCELLED), any(), any());
    }

    private EventGroupDeadlineChecker checker() {
        var readOnly = mock(StationReadOnlyGuard.class);
        when(readOnly.isWritable(anyInt())).thenReturn(true);
        return new EventGroupDeadlineChecker(
                eventGroupRepo, announcements, notifier, memberNameResolver, restrictionService, readOnly);
    }
}
