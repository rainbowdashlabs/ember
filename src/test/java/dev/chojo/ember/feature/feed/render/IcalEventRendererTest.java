/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IcalEventRendererTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private EventFieldService eventFieldService;
    private IcalEventRenderer renderer;
    private Station station;

    @BeforeEach
    void setup() {
        eventFieldService = mock(EventFieldService.class);
        // Stand-in for the real resolution: everything but a member field renders its stored text.
        when(eventFieldService.displayValue(any())).thenAnswer(inv -> {
            EventField field = inv.getArgument(0);
            return field == null || field.value() == null ? "" : field.value().trim();
        });
        NotificationService notificationService = mock(NotificationService.class);
        // Mirror NotificationService: echo the key, but interpolate {name} placeholders from the
        // params map when present so cancelledWithReason etc. surface their substitution values.
        when(notificationService.resolveLocalized(any(), eq("ical"), any(), any()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(2);
                    Map<String, String> params = inv.getArgument(3);
                    if (params == null || params.isEmpty()) return key;
                    var sb = new StringBuilder(key);
                    for (var e : params.entrySet()) {
                        sb.append(" [")
                                .append(e.getKey())
                                .append("=")
                                .append(e.getValue())
                                .append("]");
                    }
                    return sb.toString();
                });
        renderer = new IcalEventRenderer(eventFieldService, notificationService);
        station = new Station(
                1,
                null,
                "Test",
                "Europe/Berlin",
                "de-DE",
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.OFF,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false,
                false);
    }

    // -- visibility --

    @Test
    void hidesNonGuardianWhoDeclined() {
        var event = simpleEvent(10);
        var ctx = ctx(Map.of(10, RegistrationStatus.DECLINED), Map.of());
        assertFalse(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void hidesNonGuardianWhoWasDenied() {
        var event = simpleEvent(10);
        var ctx = ctx(Map.of(10, RegistrationStatus.DENIED), Map.of());
        assertFalse(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void showsNonGuardianWhoIsPending() {
        var event = simpleEvent(10);
        var ctx = ctx(Map.of(10, RegistrationStatus.PENDING), Map.of());
        assertTrue(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void hidesGuardianWhenAllManagedDeclined() {
        var event = simpleEvent(10);
        var managed = Map.of(
                10,
                List.of(
                        new IcalEventRenderer.ManagedRegistration("A", RegistrationStatus.DECLINED),
                        new IcalEventRenderer.ManagedRegistration("B", RegistrationStatus.DENIED)));
        var ctx = ctx(Map.of(), managed);
        assertFalse(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void showsGuardianWhenOneManagedAccepted() {
        var event = simpleEvent(10);
        var managed = Map.of(
                10,
                List.of(
                        new IcalEventRenderer.ManagedRegistration("A", RegistrationStatus.DECLINED),
                        new IcalEventRenderer.ManagedRegistration("B", RegistrationStatus.ACCEPTED)));
        var ctx = ctx(Map.of(), managed);
        assertTrue(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void hidesGuardianWhenOwnerAndAllManagedDeclined() {
        var event = simpleEvent(10);
        var managed = Map.of(10, List.of(new IcalEventRenderer.ManagedRegistration("A", RegistrationStatus.DECLINED)));
        var ctx = ctx(Map.of(10, RegistrationStatus.DECLINED), managed);
        assertFalse(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void showsGuardianWhenOwnerDeclinedButManagedAccepted() {
        var event = simpleEvent(10);
        var managed = Map.of(10, List.of(new IcalEventRenderer.ManagedRegistration("A", RegistrationStatus.ACCEPTED)));
        var ctx = ctx(Map.of(10, RegistrationStatus.DECLINED), managed);
        assertTrue(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void showsGuardianWhenOwnerAcceptedAndManagedDeclined() {
        var event = simpleEvent(10);
        var managed = Map.of(10, List.of(new IcalEventRenderer.ManagedRegistration("A", RegistrationStatus.DECLINED)));
        var ctx = ctx(Map.of(10, RegistrationStatus.ACCEPTED), managed);
        assertTrue(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void hidesExpiredRegistrationWhenNobodyRegistered() {
        var event = registrationRequiredEvent(10, Instant.now().minusSeconds(60));
        var ctx = ctx(Map.of(), Map.of());
        assertFalse(renderer.isVisibleForFeed(event, ctx));
    }

    @Test
    void keepsExpiredRegistrationWhenOwnerIsRegistered() {
        var event = registrationRequiredEvent(10, Instant.now().minusSeconds(60));
        var ctx = ctx(Map.of(10, RegistrationStatus.ACCEPTED), Map.of());
        assertTrue(renderer.isVisibleForFeed(event, ctx));
    }

    /**
     * An answer nobody gave before the closing date is an absence. Until then it is only an answer
     * still outstanding, and the entry stays so the reminder has something to point at.
     */
    @Test
    void anUnansweredSignUpDropsOutOnceTheClosingDatePasses() {
        var open = registrationRequiredEvent(10, Instant.now().plusSeconds(3600));
        var closed = registrationRequiredEvent(10, Instant.now().minusSeconds(60));
        var pending = ctx(Map.of(10, RegistrationStatus.PENDING), Map.of());

        assertTrue(renderer.isVisibleForFeed(open, pending), "before the closing date it stays");
        assertFalse(renderer.isVisibleForFeed(closed, pending), "after it, it counts as an absence");
    }

    @Test
    void aManagedMemberStillUnansweredAtTheClosingDateDropsOutToo() {
        var closed = registrationRequiredEvent(10, Instant.now().minusSeconds(60));
        var pending = Map.of(10, List.of(new IcalEventRenderer.ManagedRegistration("A", RegistrationStatus.PENDING)));
        var accepted = Map.of(10, List.of(new IcalEventRenderer.ManagedRegistration("A", RegistrationStatus.ACCEPTED)));

        assertFalse(renderer.isVisibleForFeed(closed, ctx(Map.of(), pending)));
        assertTrue(renderer.isVisibleForFeed(closed, ctx(Map.of(), accepted)), "a place taken keeps it");
    }

    // -- rendering --

    @Test
    void rendersLocationPropertyFromFirstLocationField() {
        var event = simpleEvent(10);
        var loc = new EventField(
                1,
                10,
                "Ort",
                EventFieldType.LOCATION,
                EventFieldConfig.parse("{}"),
                "Marktplatz 1",
                0,
                true,
                null,
                true);
        var other = new EventField(
                2, 10, "Thema", EventFieldType.STRING, EventFieldConfig.parse("{}"), "Übung", 1, false, null, false);
        when(eventFieldService.findByEvent(eq(10), any())).thenReturn(List.of(loc, other));
        var ctx = ctx(Map.of(), Map.of());

        var ve = renderer.render(event, ctx).orElseThrow();
        var location = ve.getProperty("LOCATION").map(Location.class::cast).orElseThrow();
        assertEquals("Marktplatz 1", location.getValue());
        var url = ve.getProperty("URL").map(Url.class::cast).orElseThrow();
        assertTrue(url.getValue().contains("/station/events/10?station="));
        // Description omits the LOCATION field (it lives on its own property) but keeps the other.
        String description = ve.getProperty("DESCRIPTION").orElseThrow().getValue();
        assertFalse(description.contains("Ort: Marktplatz 1"));
        assertTrue(description.contains("Thema: Übung"));
        assertTrue(description.contains("/station/events/10"));
    }

    @Test
    void rendersCancelledEventWithPrefixAndStatus() {
        var event = cancelledEvent(11, "Schlechtes Wetter");
        when(eventFieldService.findByEvent(eq(11), any())).thenReturn(List.of());
        var ctx = ctx(Map.of(), Map.of());

        var ve = renderer.render(event, ctx).orElseThrow();
        assertTrue(ve.getProperty("SUMMARY").orElseThrow().getValue().startsWith("summary.cancelledPrefix"));
        assertTrue(ve.getProperty("STATUS").isPresent());
        assertEquals(ImmutableStatus.VEVENT_CANCELLED, ve.getProperty("STATUS").orElseThrow());
        assertTrue(ve.getProperty("DESCRIPTION").orElseThrow().getValue().contains("Schlechtes Wetter"));
    }

    @Test
    void compactModeOmitsLabelsButKeepsLink() {
        var event = simpleEvent(12);
        when(eventFieldService.findByEvent(eq(12), any())).thenReturn(List.of());
        var ctx = new IcalEventRenderer.Context(
                station,
                "en",
                "https://ember.example.com",
                false, // verbose=false
                Map.of(),
                Map.of(),
                Map.of(),
                calendar(List.of()));

        var ve = renderer.render(event, ctx).orElseThrow();
        String description = ve.getProperty("DESCRIPTION").orElseThrow().getValue();
        assertTrue(description.contains("/station/events/12"));
        assertFalse(description.contains("label.eventType"));
    }

    @Test
    void rendersManagedMemberStatusLinesAndAcceptedCount() {
        var event = registrationRequiredEvent(13, Instant.now().plusSeconds(3600));
        when(eventFieldService.findByEvent(eq(13), any())).thenReturn(List.of());
        var managed = Map.of(
                13,
                List.of(
                        new IcalEventRenderer.ManagedRegistration("Alice", RegistrationStatus.ACCEPTED),
                        new IcalEventRenderer.ManagedRegistration("Bob", RegistrationStatus.DECLINED)));
        var ctx = ctx(Map.of(13, RegistrationStatus.ACCEPTED), managed);

        var ve = renderer.render(event, ctx).orElseThrow();
        String description = ve.getProperty("DESCRIPTION").orElseThrow().getValue();
        assertTrue(description.contains("Alice"));
        assertTrue(description.contains("Bob"));
        assertTrue(description.contains("label.accepted"));
        // Only one of the two managed members is ACCEPTED (Alice); owner accepts are not counted
        // in this aggregate.
        assertTrue(description.contains("1 / "));
    }

    /**
     * A series starts on its first date rather than on the day it was configured, and every date a
     * break takes out reaches the calendar as an exception, so a subscribed calendar shows the dates
     * the application shows.
     */
    @Test
    void aSeriesStartsOnItsFirstDateAndNamesItsBreaksAsExceptions() {
        Instant mondayEvening = Instant.parse("2026-08-31T16:00:00Z");
        var weekly = series(20, StationEvent.EventType.RECURRING, 3, mondayEvening, null);
        var holiday = new EventBreak(1, 1, "Ferien", LocalDate.parse("2026-09-09"), LocalDate.parse("2026-09-15"));

        var ve = renderer.render(weekly, ctxWithBreaks(List.of(holiday))).orElseThrow();

        assertEquals("20260902T160000Z", ve.getProperty("DTSTART").orElseThrow().getValue());
        assertEquals(
                "FREQ=WEEKLY;BYDAY=WE", ve.getProperty("RRULE").orElseThrow().getValue());
        assertEquals("20260909T160000Z", ve.getProperty("EXDATE").orElseThrow().getValue());
    }

    /** A quarterly series counts its quarters from its own first date, as the calendar reading it does. */
    @Test
    void aQuarterlySeriesCountsFromItsFirstDate() {
        var quarterly = series(21, StationEvent.EventType.QUARTERLY, 1, Instant.parse("2026-02-02T17:00:00Z"), null);

        var ve = renderer.render(quarterly, ctx(Map.of(), Map.of())).orElseThrow();

        assertEquals("20260202T170000Z", ve.getProperty("DTSTART").orElseThrow().getValue());
        assertEquals(
                "FREQ=MONTHLY;INTERVAL=3;BYDAY=1MO",
                ve.getProperty("RRULE").orElseThrow().getValue());
        assertTrue(ve.getProperty("EXDATE").isEmpty(), "no break, no exception");
    }

    /** A yearly series names no weekday and still repeats. */
    @Test
    void aYearlySeriesWithoutAWeekdayRepeats() {
        var yearly = series(22, StationEvent.EventType.YEARLY, null, Instant.parse("2026-05-01T08:00:00Z"), null);

        var ve = renderer.render(yearly, ctx(Map.of(), Map.of())).orElseThrow();

        assertEquals("FREQ=YEARLY", ve.getProperty("RRULE").orElseThrow().getValue());
    }

    /** A series whose end comes before its first date takes place on no day, and has no entry. */
    @Test
    void aSeriesThatNeverTakesPlaceHasNoEntry() {
        var never = series(
                23,
                StationEvent.EventType.RECURRING,
                3,
                Instant.parse("2026-09-02T16:00:00Z"),
                LocalDate.parse("2026-09-01"));

        assertTrue(renderer.render(never, ctx(Map.of(), Map.of())).isEmpty());
    }

    // -- helpers --

    private IcalEventRenderer.Context ctx(
            Map<Integer, RegistrationStatus> ownerStatus,
            Map<Integer, List<IcalEventRenderer.ManagedRegistration>> managed) {
        return new IcalEventRenderer.Context(
                station,
                "de",
                "https://ember.example.com",
                true,
                new HashMap<>(Map.of(1, new EventCategory(1, 1, "Cat", 1, null, false, null))),
                new HashMap<>(ownerStatus),
                new HashMap<>(managed),
                calendar(List.of()));
    }

    private IcalEventRenderer.Context ctxWithBreaks(List<EventBreak> breaks) {
        return new IcalEventRenderer.Context(
                station, "de", "https://ember.example.com", true, Map.of(), Map.of(), Map.of(), calendar(breaks));
    }

    private static StationCalendar calendar(List<EventBreak> breaks) {
        return new StationCalendar(BERLIN, breaks);
    }

    private StationEvent series(
            int id, StationEvent.EventType type, Integer dayOfWeek, Instant start, LocalDate repeatUntil) {
        return new StationEvent(
                id,
                1,
                "Dienst",
                null,
                type,
                dayOfWeek,
                start,
                start.plusSeconds(7200),
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                repeatUntil,
                null);
    }

    private StationEvent simpleEvent(int id) {
        return new StationEvent(
                id,
                1,
                "Probe",
                "Konzertprobe",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-09-15T09:00:00Z"),
                Instant.parse("2027-09-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                1,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private StationEvent registrationRequiredEvent(int id, Instant deadline) {
        return new StationEvent(
                id,
                1,
                "Probe",
                "Konzertprobe",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-09-15T09:00:00Z"),
                Instant.parse("2027-09-15T12:00:00Z"),
                null,
                true,
                deadline,
                false,
                1,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                10,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private StationEvent cancelledEvent(int id, String reason) {
        return new StationEvent(
                id,
                1,
                "Probe",
                "Konzertprobe",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-09-15T09:00:00Z"),
                Instant.parse("2027-09-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                1,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                true,
                Instant.now(),
                reason,
                null,
                null,
                null,
                null,
                null);
    }
}
