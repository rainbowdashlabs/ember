/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.feed.entity.StationCalendarFeed;
import dev.chojo.ember.feature.feed.entity.StationFeedLevel;
import dev.chojo.ember.feature.feed.render.IcalEventRenderer;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which appointments a station calendar feed carries at each level, and what decides its fingerprint.
 */
class StationFeedCalendarTest {
    private static final int STATION = 4;
    private static final int PUBLIC_CATEGORY = 1;
    private static final int INTERNAL_CATEGORY = 2;

    private final StationCalendarFeedService feeds = mock(StationCalendarFeedService.class);
    private final EventCrudService events = mock(EventCrudService.class);
    private final IcalEventRenderer renderer = mock(IcalEventRenderer.class);
    private final ModuleAvailability modules = mock(ModuleAvailability.class);
    private StationFeedCalendar calendars;

    private final StationEvent publicEvent =
            event(1, false, true, null, Instant.now().plus(Duration.ofDays(3)));
    private final StationEvent publicByCategory =
            event(2, false, null, PUBLIC_CATEGORY, Instant.now().plus(Duration.ofDays(4)));
    private final StationEvent internal =
            event(3, false, null, INTERNAL_CATEGORY, Instant.now().plus(Duration.ofDays(5)));
    private final StationEvent restricted =
            event(4, true, true, PUBLIC_CATEGORY, Instant.now().plus(Duration.ofDays(6)));
    private final StationEvent longAgo =
            event(5, false, true, null, Instant.now().minus(Duration.ofDays(400)));

    private static StationEvent event(
            int id, boolean restricted, @Nullable Boolean isPublic, @Nullable Integer categoryId, Instant start) {
        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(id);
        when(event.restricted()).thenReturn(restricted);
        when(event.isPublic()).thenReturn(isPublic);
        when(event.categoryId()).thenReturn(categoryId);
        when(event.startTime()).thenReturn(start);
        return event;
    }

    private static StationCalendarFeed feed(StationFeedLevel level) {
        return new StationCalendarFeed(9, STATION, "Leinwand", "token", level, null, Instant.now(), null, List.of());
    }

    @BeforeEach
    void setup() {
        var categories = mock(EventCategoryService.class);
        when(categories.findByStation(STATION))
                .thenReturn(List.of(
                        new EventCategory(PUBLIC_CATEGORY, STATION, "Öffentlich", 0, null, true, null),
                        new EventCategory(INTERNAL_CATEGORY, STATION, "Intern", 1, null, false, null)));
        var station = mock(Station.class);
        when(station.id()).thenReturn(STATION);
        when(station.name()).thenReturn("Wache");
        var stations = mock(StationRepository.class);
        when(stations.findById(STATION)).thenReturn(Optional.of(station));
        var text = mock(NotificationText.class);
        when(text.resolveLocale(any())).thenReturn("de");
        var occurrences = mock(OccurrenceCalendar.class);
        when(occurrences.forStation(STATION)).thenReturn(new StationCalendar(ZoneId.of("Europe/Berlin"), List.of()));
        when(events.findByStation(STATION))
                .thenReturn(List.of(publicEvent, publicByCategory, internal, restricted, longAgo));
        when(events.findMaxEventUpdatedAt(STATION)).thenReturn(Instant.parse("2026-11-01T00:00:00Z"));
        when(modules.disabledAt(STATION)).thenReturn(Set.of());
        when(renderer.render(any(), any())).thenReturn(List.of());
        calendars = new StationFeedCalendar(
                feeds, events, categories, stations, text, mock(EmailService.class), renderer, occurrences, modules);
    }

    private List<Integer> rendered(StationFeedLevel level) {
        calendars.calendar(feed(level), true).render().get();
        ArgumentCaptor<StationEvent> captor = ArgumentCaptor.forClass(StationEvent.class);
        verify(renderer, atLeastOnce()).render(captor.capture(), any());
        return captor.getAllValues().stream()
                .map(StationEvent::id)
                .distinct()
                .sorted()
                .toList();
    }

    @Test
    void thePublicLevelShowsWhatThePublicCalendarShows() {
        assertEquals(List.of(1, 2), rendered(StationFeedLevel.PUBLIC));
    }

    @Test
    void theMembersLevelAddsEveryUnrestrictedAppointment() {
        assertEquals(List.of(1, 2, 3), rendered(StationFeedLevel.MEMBERS));
    }

    @Test
    void theAllLevelAddsRestrictedAppointments() {
        assertEquals(List.of(1, 2, 3, 4), rendered(StationFeedLevel.ALL));
    }

    @Test
    void entriesAreRenderedForASharedCalendar() {
        calendars.calendar(feed(StationFeedLevel.ALL), true).render().get();
        ArgumentCaptor<IcalEventRenderer.Context> captor = ArgumentCaptor.forClass(IcalEventRenderer.Context.class);
        verify(renderer, atLeastOnce()).render(any(), captor.capture());
        assertEquals(IcalEventRenderer.Reader.STATION, captor.getValue().reader());
    }

    @Test
    void aFetchIsRecorded() {
        calendars.calendar(feed(StationFeedLevel.PUBLIC), false);
        verify(feeds).recordPoll(9);
    }

    @Test
    void changingTheLevelChangesTheFingerprint() {
        var before = calendars.calendar(feed(StationFeedLevel.PUBLIC), false).fingerprint();
        var after = calendars.calendar(feed(StationFeedLevel.ALL), false).fingerprint();
        assertNotEquals(before.etag(), after.etag());
    }

    @Test
    void anAppointmentGoneChangesTheFingerprint() {
        var before = calendars.calendar(feed(StationFeedLevel.ALL), false).fingerprint();
        when(events.findByStation(STATION)).thenReturn(List.of(publicEvent, internal));
        var after = calendars.calendar(feed(StationFeedLevel.ALL), false).fingerprint();
        assertNotEquals(before.etag(), after.etag());
    }

    @Test
    void aStationWithAppointmentsSwitchedOffHasNoFeed() {
        when(modules.disabledAt(STATION)).thenReturn(Set.of(StationModule.EVENTS));
        assertEquals(
                GeneralRefusal.MODULE_SWITCHED_OFF,
                assertThrows(RefusalResponse.class, () -> calendars.calendar(feed(StationFeedLevel.ALL), false))
                        .refusal());
    }

    @Test
    void anUnknownAddressLeadsNowhere() {
        when(feeds.findByToken("gone")).thenReturn(Optional.empty());
        assertEquals(
                FeedRefusal.FEED_LINK_NOT_GOOD,
                assertThrows(RefusalResponse.class, () -> calendars.feed("gone"))
                        .refusal());
    }
}
