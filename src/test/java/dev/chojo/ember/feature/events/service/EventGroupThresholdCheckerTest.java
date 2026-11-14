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
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An event group with a minimum: it is called off as a whole, with the notice saying why, once its
 * first part is within the days it names and too few sign-ups were accepted; otherwise it is left
 * alone. And the minimum is only stored complete.
 */
class EventGroupThresholdCheckerTest extends RepositoryTestBase {
    private static Station station;
    private static final List<StationMember> members = new ArrayList<>();
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventGroupRepository eventGroupRepo = new EventGroupRepository();
    private final EventGroupRegistrationRepository signUpRepo = new EventGroupRegistrationRepository();
    private EventGroupService eventGroups;
    private DomainEventBus bus;
    private StationReadOnlyGuard readOnly;
    private EventGroupThresholdChecker checker;
    private EventGroupCancellationService cancellation;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event group Threshold Station");
        for (int i = 0; i < 3; i++) {
            var account = accountRepo.create("member" + i + "@group-threshold.test", "Mia" + i, "Schwelle");
            accounts.add(account.id());
            members.add(stationMemberRepo.create(station.id(), account.id()));
        }
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        bus = mock(DomainEventBus.class);
        eventGroups = newEventGroupService();
        var questions = new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility);
        var partnerRepo = new EventGroupPartnerRepository();
        var registrations = new EventGroupRegistrationService(
                eventGroupRepo,
                signUpRepo,
                eventRepo,
                eventRegistrationRepo,
                newEventServices(new DomainEventBus(Set.of())).registration(),
                occurrenceCalendar,
                mock(DomainEventBus.class),
                memberNameResolver,
                questions,
                partnerRepo);
        cancellation = new EventGroupCancellationService(
                eventGroupRepo,
                signUpRepo,
                eventRepo,
                newEventServices(new DomainEventBus(Set.of())).cancellation(),
                occurrenceCalendar,
                bus);
        readOnly = mock(StationReadOnlyGuard.class);
        when(readOnly.isWritable(anyInt())).thenReturn(true);
        checker = new EventGroupThresholdChecker(
                eventGroupRepo, signUpRepo, partnerRepo, registrations, cancellation, occurrenceCalendar, readOnly);
    }

    private EventGroup withMinimum(String name, int minimum, int days, int... partsInDays) {
        var created = eventGroups.create(
                station.id(),
                new EventGroupDetails(name, null, null, null, false, null, minimum, days),
                List.of(new EventGroupSetDraft(null, "Übung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        int setId = eventGroups.findSets(created.id()).getFirst().id();
        for (int daysAhead : partsInDays) {
            var zone = occurrenceCalendar.zoneOf(station.id());
            var start = LocalDate.now(zone)
                    .plusDays(daysAhead)
                    .atTime(9, 0)
                    .atZone(zone)
                    .toInstant();
            var event = eventRepo.create(
                    station.id(),
                    name + " Tag " + daysAhead,
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
            eventGroups.addPart(station.id(), created.id(), event.id(), setId);
        }
        return created;
    }

    private void accept(EventGroup eventGroup, int count) {
        for (int i = 0; i < count; i++) {
            signUpRepo.signUp(eventGroup.id(), members.get(i).id(), RegistrationStatus.ACCEPTED, null);
        }
    }

    private boolean cancelled(EventGroup eventGroup) {
        return eventGroupRepo.findById(eventGroup.id()).orElseThrow().cancelled();
    }

    private List<EventGroupCancelled> published() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(bus, atLeast(0)).publish(captor.capture());
        return captor.getAllValues().stream()
                .filter(EventGroupCancelled.class::isInstance)
                .map(EventGroupCancelled.class::cast)
                .toList();
    }

    @Test
    void tooFewSignUpsWithinTheDaysCallTheWholeEventGroupOff() {
        var eventGroup = withMinimum("Zu wenige", 2, 5, 3, 10);
        accept(eventGroup, 1);
        signUpRepo.signUp(eventGroup.id(), members.get(2).id(), RegistrationStatus.PENDING, null);

        checker.check();

        assertTrue(cancelled(eventGroup));
        var notice = published().getFirst();
        assertEquals(eventGroup.id(), notice.eventGroupId());
        assertEquals(CancellationCause.THRESHOLD, notice.cause());
        assertNull(notice.reason());
        assertEquals(List.of(members.get(0).id(), members.get(2).id()), notice.memberIds());
    }

    @Test
    void enoughAcceptedSignUpsKeepTheEventGroup() {
        var eventGroup = withMinimum("Genug", 2, 5, 3);
        accept(eventGroup, 2);

        checker.check();

        assertFalse(cancelled(eventGroup));
    }

    @Test
    void anEventGroupWhoseFirstPartIsStillFarOffIsLeftAlone() {
        var eventGroup = withMinimum("Noch Zeit", 2, 5, 9);

        checker.check();

        assertFalse(cancelled(eventGroup));
    }

    @Test
    void anEventGroupThatHasBegunIsLeftAlone() {
        var eventGroup = withMinimum("Begonnen", 2, 5, -1, 2);

        checker.check();

        assertFalse(cancelled(eventGroup));
    }

    @Test
    void anEventGroupWithoutPartsOrOfAReadOnlyStationIsLeftAlone() {
        var empty = withMinimum("Leer", 2, 5);
        var readOnlyGroup = withMinimum("Nur lesen", 2, 5, 1);
        when(readOnly.isWritable(anyInt())).thenReturn(false);

        checker.check();
        assertFalse(cancelled(readOnlyGroup));

        when(readOnly.isWritable(anyInt())).thenReturn(true);
        checker.check();
        assertFalse(cancelled(empty));
        assertTrue(cancelled(readOnlyGroup));
    }

    @Test
    void aCalledOffEventGroupIsNotCalledOffAgain() {
        var eventGroup = withMinimum("Einmal", 2, 5, 1);

        checker.check();
        checker.check();

        assertEquals(
                1,
                published().stream()
                        .filter(notice -> notice.eventGroupId() == eventGroup.id())
                        .count());
    }

    @Test
    void aMinimumIsStoredOnlyWhole() {
        for (var incomplete : List.of(
                new EventGroupDetails("Halb", null, null, null, false, null, 2, null),
                new EventGroupDetails("Halb", null, null, null, false, null, null, 3),
                new EventGroupDetails("Null", null, null, null, false, null, 0, 3),
                new EventGroupDetails("Vorher", null, null, null, false, null, 2, -1))) {
            var thrown = assertThrows(
                    RefusalResponse.class,
                    () -> eventGroups.create(
                            station.id(),
                            incomplete,
                            List.of(new EventGroupSetDraft(null, "Übung", 1)),
                            RestrictionAudience.empty(),
                            RestrictionAudience.empty(),
                            null));
            assertEquals(EventRefusal.EVENT_GROUP_MINIMUM_INCOMPLETE, thrown.refusal());
        }
        var stored = withMinimum("Ganz", 4, 0);
        assertEquals(4, stored.minRegistrations());
        assertEquals(0, stored.thresholdDays());
        eventGroups.update(station.id(), stored.id(), new EventGroupDetails("Ganz", null, null, null, false, null));
        var cleared = eventGroupRepo.findById(stored.id()).orElseThrow();
        assertNull(cleared.minRegistrations());
        assertNull(cleared.thresholdDays());
    }

    @Test
    void theCheckRunsEveryHalfHour() {
        var task = checker.scheduledTasks().getFirst();

        assertEquals("event-group-threshold-check", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(7), Duration.ofMinutes(30)), task.schedule());
        task.work().run();
    }

    /** What is read again under the lock decides: an event group no longer short stays. */
    @Test
    void anEventGroupNoLongerShortUnderTheLockStays() {
        var eventGroup = withMinimum("Inzwischen genug", 2, 5, 3);

        assertFalse(cancellation.cancelForTooFewSignUps(station.id(), eventGroup.id(), () -> false));
        assertFalse(cancelled(eventGroup));
        assertTrue(cancellation.cancelForTooFewSignUps(station.id(), eventGroup.id(), () -> true));
        assertTrue(cancelled(eventGroup));
    }
}
