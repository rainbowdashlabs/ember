/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.ChangePreview;
import dev.chojo.ember.feature.events.entity.EditScope;
import dev.chojo.ember.feature.events.entity.EditScopeKind;
import dev.chojo.ember.feature.events.entity.EventDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** What the save dialog is told before anything is saved. */
class EventChangePreviewServiceTest extends RepositoryTestBase {

    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static StationMember carla;
    private static final List<Integer> accounts = new ArrayList<>();

    private final List<Integer> events = new ArrayList<>();
    private Notifier notifications;
    private EventChangePreviewService preview;
    private LocalDate today;
    private ZoneId zone;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Change Preview Station");
        anna = memberOf("anna@preview.test", "Anna");
        ben = memberOf("ben@preview.test", "Ben");
        carla = memberOf("carla@preview.test", "Carla");
    }

    private static StationMember memberOf(String email, String firstName) {
        var account = accountRepo.create(email, firstName, "Preview");
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
        var restrictions = new EventRestrictionService(eventRepo, restrictionService);
        var audience = new EventAudience(eventRegistrationRepo, stationMemberRepo, restrictions, occurrenceCalendar);
        var notifier = new EventChangeNotifier(
                occurrenceCalendar,
                audience,
                eventRegistrationRepo,
                eventRepo,
                memberNameResolver,
                notifications,
                mock(StationReadOnlyGuard.class));
        preview = new EventChangePreviewService(occurrenceCalendar, audience, eventRegistrationRepo, notifier);
        today = occurrenceCalendar.forStation(station.id()).today();
        zone = occurrenceCalendar.zoneOf(station.id());
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
                null,
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

    private StationEvent weekly() {
        LocalDate first = today.minusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        return create(StationEvent.EventType.RECURRING, DayOfWeek.TUESDAY.getValue(), first, true);
    }

    private LocalDate nextTuesday() {
        return today.plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    }

    private void register(StationEvent event, StationMember member, LocalDate date, RegistrationStatus status) {
        eventRegistrationRepo.create(event.id(), member.id(), date, status, null);
    }

    private static EventDraft later(StationEvent event, long hours) {
        return new EventDraft(
                event.name(),
                "Anders",
                event.eventType(),
                event.dayOfWeek(),
                event.startTime().plus(hours, ChronoUnit.HOURS),
                event.endTime().plus(hours, ChronoUnit.HOURS),
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

    @Test
    void theCountsFollowTheScope() {
        var series = weekly();
        register(series, anna, nextTuesday(), RegistrationStatus.ACCEPTED);
        register(series, ben, nextTuesday().plusWeeks(1), RegistrationStatus.PENDING);
        register(series, carla, nextTuesday().plusWeeks(2), RegistrationStatus.DECLINED);
        var draft = later(series, 1);

        assertEquals(new ChangePreview(2, true, "19:00", 2, 2), preview.preview(series, draft, EditScope.ALL));
        assertEquals(
                new ChangePreview(1, true, "19:00", 1, 1),
                preview.preview(series, draft, new EditScope(EditScopeKind.THIS, nextTuesday())));
        assertEquals(
                new ChangePreview(1, true, "19:00", 1, 1),
                preview.preview(
                        series,
                        draft,
                        new EditScope(
                                EditScopeKind.THIS_AND_LATER, nextTuesday().plusWeeks(1))));
    }

    /** Places on dates the saved series no longer falls on are withdrawn, so they are not counted. */
    @Test
    void placesOnDatesTheSaveDropsAreNotCounted() {
        var series = weekly();
        register(series, anna, nextTuesday(), RegistrationStatus.ACCEPTED);
        register(series, ben, nextTuesday().plusWeeks(3), RegistrationStatus.ACCEPTED);
        var draft = later(series, 1);
        var onWednesday = new EventDraft(
                draft.name(),
                draft.description(),
                draft.eventType(),
                DayOfWeek.WEDNESDAY.getValue(),
                draft.startTime(),
                draft.endTime(),
                null,
                true,
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
        var shorter = new EventDraft(
                draft.name(),
                draft.description(),
                draft.eventType(),
                draft.dayOfWeek(),
                draft.startTime(),
                draft.endTime(),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                nextTuesday().plusWeeks(1),
                null);

        assertEquals(new ChangePreview(0, true, "19:00", 0, 0), preview.preview(series, onWednesday, EditScope.ALL));
        assertEquals(new ChangePreview(1, true, "19:00", 1, 1), preview.preview(series, shorter, EditScope.ALL));
    }

    @Test
    void aChangeThatKeepsTheStartMovesNobody() {
        var oneOff = create(StationEvent.EventType.ONE_TIME, null, today.plusDays(3), false);
        register(oneOff, ben, today.plusDays(3), RegistrationStatus.DECLINED);

        var counts = preview.preview(oneOff, later(oneOff, 0), EditScope.ALL);

        assertEquals(new ChangePreview(2, false, null, 0, 2), counts);
    }

    @Test
    void aPreviewWritesNothingAndTellsNobody() {
        var oneOff = create(StationEvent.EventType.ONE_TIME, null, today.plusDays(3), true);
        register(oneOff, anna, today.plusDays(3), RegistrationStatus.ACCEPTED);

        var counts = preview.preview(oneOff, later(oneOff, 24), EditScope.ALL);

        assertEquals(new ChangePreview(1, true, "18:00", 1, 1), counts);
        assertEquals(
                oneOff.startTime(),
                eventRepo.findById(oneOff.id()).orElseThrow().startTime());
        assertEquals(List.of(anna.id()), eventRegistrationRepo.findRegisteredMemberIds(oneOff.id(), today.plusDays(3)));
        verifyNoInteractions(notifications);
    }

    @Test
    void aPastAppointmentTellsNobodyAndAScopeNeedsItsDate() {
        var past = create(StationEvent.EventType.ONE_TIME, null, today.minusDays(3), true);
        register(past, anna, today.minusDays(3), RegistrationStatus.ACCEPTED);

        assertEquals(new ChangePreview(0, false, null, 0, 0), preview.preview(past, later(past, 1), EditScope.ALL));
        var series = weekly();
        var thrown = assertThrows(
                RefusalResponse.class,
                () -> preview.preview(series, later(series, 1), new EditScope(EditScopeKind.THIS, null)));
        assertEquals(EventRefusal.EVENT_SCOPE_NEEDS_A_DATE, thrown.refusal());
        var noKind = assertThrows(
                RefusalResponse.class,
                () -> preview.preview(series, later(series, 1), new EditScope(null, nextTuesday())));
        assertEquals(EventRefusal.EVENT_SCOPE_NEEDS_A_KIND, noKind.refusal());
        var notADate = assertThrows(
                RefusalResponse.class,
                () -> preview.preview(
                        series,
                        later(series, 1),
                        new EditScope(EditScopeKind.THIS, nextTuesday().plusDays(1))));
        assertEquals(EventRefusal.EVENT_SCOPE_DATE_NOT_AN_OCCURRENCE, notADate.refusal());
        assertTrue(eventRepo.findContinuations(series.id()).isEmpty());
    }
}
