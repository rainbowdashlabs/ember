/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.ChangeNotice;
import dev.chojo.ember.feature.events.entity.EventChangePart;
import dev.chojo.ember.feature.events.entity.EventDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.handler.EventChangedHandler;
import dev.chojo.ember.feature.events.repository.EventHandoverRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Who hears about a saved change, and in how many messages. */
class EventChangeNotifierTest extends RepositoryTestBase {

    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static StationMember carla;
    private static final List<Integer> accounts = new ArrayList<>();

    private final List<Integer> events = new ArrayList<>();
    private Notifier notifications;
    private StationReadOnlyGuard readOnlyGuard;
    private FederatedEventChangeService federated;
    private EventChangeNotifier changeNotifier;
    private EventEditScopeService scope;
    private EventRestrictionService restrictions;
    private LocalDate today;
    private ZoneId zone;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Change Notice Station");
        anna = memberOf("anna@notice.test", "Anna");
        ben = memberOf("ben@notice.test", "Ben");
        carla = memberOf("carla@notice.test", "Carla");
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
        notifications = mock(Notifier.class);
        readOnlyGuard = mock(StationReadOnlyGuard.class);
        federated = mock(FederatedEventChangeService.class);
        when(readOnlyGuard.isWritable(anyInt())).thenReturn(true);
        restrictions = new EventRestrictionService(eventRepo, restrictionService);
        var calendar = occurrenceCalendar;
        var audience = new EventAudience(eventRegistrationRepo, stationMemberRepo, restrictions, calendar);
        changeNotifier = new EventChangeNotifier(
                calendar,
                audience,
                eventRegistrationRepo,
                eventRepo,
                memberNameResolver,
                notifications,
                readOnlyGuard,
                federated);
        var moveService = new EventMoveService(
                eventRegistrationRepo,
                eventFieldRepo,
                eventReminderRepo,
                eventDateCancellationRepo,
                calendar,
                memberNameResolver,
                notifications);
        var crud = newEventServices(new DomainEventBus(Set.of(new EventChangedHandler(() -> moveService))))
                .crud();
        scope = new EventEditScopeService(
                eventRepo,
                crud,
                new EventHandoverRepository(),
                eventDetachedDateRepo,
                calendar,
                moveService,
                mock(FederatedEventChangeService.class));
        today = calendar.forStation(station.id()).today();
        zone = calendar.zoneOf(station.id());
    }

    @AfterEach
    void removeEvents() {
        events.forEach(eventRepo::delete);
        events.clear();
    }

    private StationEvent create(StationEvent.EventType type, Integer dayOfWeek, LocalDate day, boolean registration) {
        Instant start = day.atTime(18, 0).atZone(zone).toInstant();
        var event = eventRepo.create(
                station.id(),
                "Dienst",
                "Wie immer",
                type,
                dayOfWeek,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                registration,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        events.add(event.id());
        return event;
    }

    private StationEvent oneOff(boolean registration) {
        return create(StationEvent.EventType.ONE_TIME, null, today.plusDays(3), registration);
    }

    private StationEvent weekly(boolean registration) {
        LocalDate first = today.minusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        return create(StationEvent.EventType.RECURRING, DayOfWeek.TUESDAY.getValue(), first, registration);
    }

    private LocalDate nextTuesday() {
        return today.plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    }

    private void register(StationEvent event, StationMember member, LocalDate date, RegistrationStatus status) {
        eventRegistrationRepo.create(event.id(), member.id(), date, status, null);
    }

    private static EventDraft draftOf(StationEvent event, String description, long laterByHours) {
        return new EventDraft(
                event.name(),
                description,
                event.eventType(),
                event.dayOfWeek(),
                event.startTime().plus(laterByHours, ChronoUnit.HOURS),
                event.endTime().plus(laterByHours, ChronoUnit.HOURS),
                null,
                event.requiresRegistration(),
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Saves a change of the whole appointment the way the route does and tells afterwards. */
    private StationEvent changeWhole(StationEvent before, String description, long laterByHours, ChangeNotice notice) {
        var after = scope.save(
                before,
                draftOf(before, description, laterByHours),
                dev.chojo.ember.feature.events.entity.EditScope.ALL);
        changeNotifier.tellChanged(before, after, notice);
        return after;
    }

    private static ChangeNotice telling(String note, EventChangePart... parts) {
        return new ChangeNotice(true, List.of(parts), note);
    }

    /** What each member's household was sent, by member, read from the notifier. */
    private Map<Integer, List<Sent>> sent() {
        var audiences = ArgumentCaptor.forClass(StationAudience.class);
        var types = ArgumentCaptor.forClass(NotificationType.class);
        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifications, atLeast(0)).notify(audiences.capture(), types.capture(), data.capture(), any());
        var byMember = new HashMap<Integer, List<Sent>>();
        for (int i = 0; i < types.getAllValues().size(); i++) {
            var type = types.getAllValues().get(i);
            if (type != NotificationType.EVENT_MOVED && type != NotificationType.EVENT_CHANGED) continue;
            var params = data.getAllValues().get(i).params();
            String name = params instanceof NotificationParams.EventChangeNotice c
                    ? c.memberName()
                    : ((NotificationParams.EventMoved) params).memberName();
            int member = name.startsWith("Anna") ? anna.id() : name.startsWith("Ben") ? ben.id() : carla.id();
            byMember.computeIfAbsent(member, k -> new ArrayList<>()).add(new Sent(type, params));
        }
        return byMember;
    }

    private record Sent(NotificationType type, NotificationParams params) {}

    @Test
    void withoutANoticeOnlyAMovedStartIsTold() {
        var event = oneOff(true);
        register(event, anna, today.plusDays(3), RegistrationStatus.ACCEPTED);
        register(event, ben, today.plusDays(3), RegistrationStatus.DECLINED);

        changeWhole(event, "Anders", 1, ChangeNotice.SILENT);

        var sent = sent();
        assertEquals(Set.of(anna.id()), sent.keySet());
        assertEquals(
                NotificationType.EVENT_MOVED, sent.get(anna.id()).getFirst().type());
        assertEquals(
                "19:00",
                ((NotificationParams.EventMoved) sent.get(anna.id()).getFirst().params()).startTime());
    }

    @Test
    void aNoticeWithoutAMoveTellsTheChangedPartsAndTheNote() {
        var event = oneOff(true);
        register(event, anna, today.plusDays(3), RegistrationStatus.PENDING);

        changeWhole(event, "Anders", 0, telling("  Helm mitbringen  ", EventChangePart.DESCRIPTION));

        var notice = (NotificationParams.EventChangeNotice)
                sent().get(anna.id()).getFirst().params();
        assertEquals(List.of(EventChangePart.DESCRIPTION), notice.parts());
        assertEquals("Helm mitbringen", notice.note());
        assertNull(notice.startTime());
    }

    @Test
    void aMoveAndANoticeReachEachPlaceHolderOnce() {
        var event = oneOff(true);
        register(event, anna, today.plusDays(3), RegistrationStatus.ACCEPTED);

        changeWhole(event, "Anders", 2, telling(null, EventChangePart.DESCRIPTION));

        var toAnna = sent().get(anna.id());
        assertEquals(1, toAnna.size());
        var notice = (NotificationParams.EventChangeNotice) toAnna.getFirst().params();
        assertEquals(NotificationType.EVENT_CHANGED, toAnna.getFirst().type());
        assertEquals(List.of(EventChangePart.TIME, EventChangePart.DESCRIPTION), notice.parts());
        assertEquals("20:00", notice.startTime());
        verify(federated)
                .tellPartners(any(), eq(today.plusDays(3)), eq("TIME,DESCRIPTION"), isNull(), eq("20:00"), eq(true));
    }

    /**
     * Without registration the notice goes to everybody who sees the appointment and did not decline;
     * a place holder who cannot see it hears only about the move.
     */
    @Test
    void disjointAudiencesEachGetTheirOwnMessage() {
        var event = oneOff(false);
        LocalDate day = today.plusDays(3);
        register(event, carla, day, RegistrationStatus.PENDING);
        register(event, ben, day, RegistrationStatus.DECLINED);
        restrictions.setViewRestrictions(
                event.id(),
                new RestrictionSelection(
                        List.of(), List.of(), List.of(), List.of(anna.id(), ben.id()), RestrictionMode.OR));
        restrictions.updateViewRestrictionMode(event.id(), RestrictionMode.OR);

        changeWhole(event, "Anders", 1, telling(null, EventChangePart.DESCRIPTION));

        var sent = sent();
        assertEquals(Set.of(anna.id(), carla.id()), sent.keySet());
        assertEquals(
                NotificationType.EVENT_CHANGED, sent.get(anna.id()).getFirst().type());
        assertEquals(
                NotificationType.EVENT_MOVED, sent.get(carla.id()).getFirst().type());
    }

    @Test
    void aSeriesWhoseTimeChangesTellsEveryPlaceAhead() {
        var event = weekly(true);
        register(event, anna, nextTuesday(), RegistrationStatus.ACCEPTED);
        register(event, ben, nextTuesday().plusWeeks(2), RegistrationStatus.PENDING);
        register(
                event,
                carla,
                today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY)),
                RegistrationStatus.ACCEPTED);

        changeWhole(event, "Wie immer", 1, ChangeNotice.SILENT);

        var sent = sent();
        assertEquals(Set.of(anna.id(), ben.id()), sent.keySet());
        assertEquals(
                nextTuesday().plusWeeks(2),
                ((NotificationParams.EventMoved) sent.get(ben.id()).getFirst().params()).eventDate(),
                "each member is told about the first date they hold a place on");
    }

    @Test
    void aNoticeAfterADetachTellsTheMovedDateOnly() {
        var event = weekly(true);
        register(event, anna, nextTuesday(), RegistrationStatus.ACCEPTED);
        register(event, ben, nextTuesday().plusWeeks(1), RegistrationStatus.ACCEPTED);
        var detached = scope.detach(event, nextTuesday(), draftOf(event, "Anders", 1));
        events.add(detached.id());

        changeNotifier.tellContinued(detached, telling(null, EventChangePart.DESCRIPTION));

        var sent = sent();
        assertEquals(Set.of(anna.id()), sent.keySet());
        var notice = (NotificationParams.EventChangeNotice)
                sent.get(anna.id()).getFirst().params();
        assertEquals(List.of(EventChangePart.TIME, EventChangePart.DESCRIPTION), notice.parts());
        assertEquals(nextTuesday(), notice.eventDate());
    }

    @Test
    void aSplitWithoutANoticeTellsTheMovedStartFromItsDateOn() {
        var event = weekly(true);
        register(event, anna, nextTuesday(), RegistrationStatus.ACCEPTED);
        register(event, ben, nextTuesday().plusWeeks(1), RegistrationStatus.ACCEPTED);
        var continued = scope.split(event, nextTuesday().plusWeeks(1), draftOf(event, "Wie immer", 1));
        events.add(continued.id());

        changeNotifier.tellContinued(continued, ChangeNotice.SILENT);

        var sent = sent();
        assertEquals(Set.of(ben.id()), sent.keySet());
        assertEquals(NotificationType.EVENT_MOVED, sent.get(ben.id()).getFirst().type());
    }

    @Test
    void aContinuedAppointmentIsToldOnceAndOnlyAContinuationIsTold() {
        var event = weekly(true);
        register(event, anna, nextTuesday(), RegistrationStatus.ACCEPTED);
        var detached = scope.detach(event, nextTuesday(), draftOf(event, "Anders", 1));
        events.add(detached.id());

        changeNotifier.tellContinued(detached, ChangeNotice.SILENT);
        changeNotifier.tellContinued(detached, telling(null, EventChangePart.DESCRIPTION));

        assertEquals(1, sent().get(anna.id()).size(), "a repeated notice tells nobody again");
        var unlinked = oneOff(true);
        var thrown =
                assertThrows(RefusalResponse.class, () -> changeNotifier.tellContinued(unlinked, ChangeNotice.SILENT));
        assertEquals(EventRefusal.EVENT_NOTICE_NOT_A_CONTINUATION, thrown.refusal());
    }

    @Test
    void aMovedStartIsToldOnItsOwnWhereTheEditorNeverSentTheNotice() {
        var event = weekly(true);
        register(event, anna, nextTuesday(), RegistrationStatus.ACCEPTED);
        var detached = scope.detach(event, nextTuesday(), draftOf(event, "Anders", 1));
        events.add(detached.id());
        var task = changeNotifier.scheduledTasks().getFirst();

        task.work().run();
        assertTrue(sent().isEmpty(), "the editor still has time to send it");

        query("UPDATE station_event SET change_notice_due_at = now() - INTERVAL '1 hour' WHERE id = :id;")
                .single(call().bind("id", detached.id()))
                .update();
        when(readOnlyGuard.isWritable(station.id())).thenReturn(false);
        task.work().run();
        assertTrue(sent().isEmpty(), "a station that is read only is not told for");

        when(readOnlyGuard.isWritable(station.id())).thenReturn(true);
        task.work().run();
        task.work().run();

        assertEquals("event-change-notice-overdue", task.name());
        assertEquals(1, sent().get(anna.id()).size());
        assertEquals(
                NotificationType.EVENT_MOVED, sent().get(anna.id()).getFirst().type());
    }

    @Test
    void withoutRegistrationTheNoticeReachesEverybodyWhoDidNotDecline() {
        var event = oneOff(false);
        register(event, ben, today.plusDays(3), RegistrationStatus.DECLINED);

        changeWhole(event, "Anders", 0, telling(null, EventChangePart.DESCRIPTION));

        var sent = sent();
        assertTrue(sent.containsKey(anna.id()));
        assertTrue(sent.containsKey(carla.id()));
        assertTrue(!sent.containsKey(ben.id()));
    }

    @Test
    void nothingIsToldWhereNothingChangedOrNothingIsAhead() {
        var event = oneOff(true);
        register(event, anna, today.plusDays(3), RegistrationStatus.ACCEPTED);
        changeWhole(event, "Wie immer", 0, new ChangeNotice(true, List.of(), "Nur ein Hinweis"));

        var past = create(StationEvent.EventType.ONE_TIME, null, today.minusDays(3), true);
        register(past, anna, today.minusDays(3), RegistrationStatus.ACCEPTED);
        changeWhole(past, "Anders", 1, telling(null, EventChangePart.DESCRIPTION));

        assertTrue(sent().isEmpty());
    }

    @Test
    void aNoteLongerThanANoticeCarriesIsRefused() {
        var thrown = assertThrows(
                RefusalResponse.class,
                () -> changeNotifier.requireValid(telling("x".repeat(501), EventChangePart.DESCRIPTION)));
        assertEquals(EventRefusal.EVENT_NOTE_TOO_LONG, thrown.refusal());
        changeNotifier.requireValid(telling(" " + "x".repeat(500) + " ", EventChangePart.DESCRIPTION));
        changeNotifier.requireValid(new ChangeNotice(false, null, "x".repeat(600)));
    }
}
