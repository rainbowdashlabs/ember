/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EditScope;
import dev.chojo.ember.feature.events.entity.EditScopeKind;
import dev.chojo.ember.feature.events.entity.EventDraft;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventLink;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventHandoverRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** One date of a series edited on its own, and a series edited from one date on. */
class EventEditScopeServiceTest extends RepositoryTestBase {

    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventRegistrationFieldRepository registrationFields = new EventRegistrationFieldRepository();
    private final List<Integer> events = new ArrayList<>();
    private EventCrudService crud;
    private EventEditScopeService scope;
    private LocalDate today;
    private ZoneId zone;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Edit Scope Station");
        anna = memberOf("anna@scope.test", "Anna");
        ben = memberOf("ben@scope.test", "Ben");
    }

    private static StationMember memberOf(String email, String firstName) {
        var account = accountRepo.create(email, firstName, "Scope");
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
        var moveService = new EventMoveService(
                eventRegistrationRepo,
                eventFieldRepo,
                eventReminderRepo,
                eventDateCancellationRepo,
                occurrenceCalendar,
                memberNameResolver,
                mock(Notifier.class));
        crud = newEventServices(new DomainEventBus(Set.of())).crud();
        scope = new EventEditScopeService(
                eventRepo,
                crud,
                new EventHandoverRepository(),
                eventDetachedDateRepo,
                occurrenceCalendar,
                moveService,
                mock(FederatedEventChangeService.class));
        var calendar = occurrenceCalendar.forStation(station.id());
        today = calendar.today();
        zone = calendar.zone();
    }

    @AfterEach
    void removeEvents() {
        events.forEach(eventRepo::delete);
        events.clear();
    }

    /** A weekly series on Tuesdays at six in the evening that started a month ago. */
    private StationEvent weekly(@Nullable LocalDate until, @Nullable Integer count) {
        LocalDate first = today.minusWeeks(4).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        return weeklyFrom(first, until, count);
    }

    private StationEvent weeklyFrom(LocalDate first, @Nullable LocalDate until, @Nullable Integer count) {
        Instant start = first.atTime(18, 0).atZone(zone).toInstant();
        var event = crud.create(
                station.id(),
                "Dienst",
                "Wie immer",
                StationEvent.EventType.RECURRING,
                DayOfWeek.TUESDAY.getValue(),
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        events.add(event.id());
        if (until != null || count != null) eventRepo.updateRepeatEnd(event.id(), until, count);
        return eventRepo.findById(event.id()).orElseThrow();
    }

    /** The series as the editor saves it: renamed, an hour later, and with the end it had. */
    private static EventDraft edited(StationEvent series, StationEvent.EventType type) {
        return new EventDraft(
                "Dienst neu",
                "Anders",
                type,
                series.dayOfWeek(),
                series.startTime().plus(1, ChronoUnit.HOURS),
                series.endTime().plus(1, ChronoUnit.HOURS),
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
                series.repeatUntil(),
                series.repeatCount());
    }

    private LocalDate nextTuesday() {
        return today.plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    }

    private EventRegistration register(StationEvent event, StationMember member, LocalDate date) {
        return eventRegistrationRepo.create(event.id(), member.id(), date, RegistrationStatus.ACCEPTED, null);
    }

    private StationEvent track(StationEvent event) {
        events.add(event.id());
        return event;
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }

    /**
     * Only this date: the date becomes a one-off at the edited time, takes its places, answers,
     * question values, cancellation and sheet along, and the series skips it from then on while the
     * dates around it stay where they were.
     */
    @Test
    void aDetachedDateBecomesAOneOffAndTakesWhatIsFiledAgainstIt() {
        var series = weekly(null, null);
        LocalDate date = nextTuesday();
        var question = registrationFields.create(
                series.id(), "Essen", FieldType.TEXT, EventQuestionSettings.empty(), 0, false);
        var field = eventFieldRepo
                .create(
                        series.id(),
                        "Fahrer",
                        FieldType.TEXT,
                        new EventFieldConfig(null, null, null, null, true, null, true).settings(),
                        "",
                        0,
                        false,
                        null,
                        false)
                .id();
        eventReminderRepo.replace(series.id(), List.of(1));
        var place = register(series, anna, date);
        registrationFields.setValue(place.id(), question.id(), "vegetarisch");
        var later = register(series, ben, date.plusWeeks(1));
        eventFieldRepo.updateValueOn(field, date, "Bus");
        eventFieldRepo.updateValueOn(field, date.plusWeeks(1), "Auto");
        eventReminderRepo.markSent(series.id(), date, 1);
        var template = attendanceRepo.createTemplate(station.id(), "Scope Sheet");
        var span = series.occurrenceOn(date, zone).orElseThrow();
        var sheet = attendanceRepo.createSession(template.id(), span.start(), span.end(), series.id(), null, null);

        var detached = track(scope.detach(series, date, edited(series, StationEvent.EventType.RECURRING)));

        assertNotEquals(series.id(), detached.id());
        assertEquals(StationEvent.EventType.ONE_TIME, detached.eventType());
        assertEquals("Dienst neu", detached.name());
        assertEquals(date.atTime(19, 0).atZone(zone).toInstant(), detached.startTime());
        assertEquals(
                new EventLink(series.id(), "Dienst", date, true),
                eventRepo.findContinues(detached.id()).orElseThrow());
        assertEquals(
                List.of(new EventLink(detached.id(), "Dienst neu", date, false)),
                eventRepo.findContinuations(series.id()));

        var moved = eventRegistrationRepo.findById(place.id()).orElseThrow();
        assertEquals(detached.id(), moved.eventId());
        var copiedQuestion = registrationFields.findByEvent(detached.id()).getFirst();
        assertEquals(
                "vegetarisch",
                registrationFields.findValues(place.id()).getFirst().value());
        assertEquals(
                copiedQuestion.id(),
                registrationFields.findValues(place.id()).getFirst().fieldId());
        assertEquals(
                series.id(),
                eventRegistrationRepo.findById(later.id()).orElseThrow().eventId());

        var copiedField = eventFieldRepo.findByEvent(detached.id()).getFirst().id();
        assertEquals(
                "Bus",
                eventFieldRepo.findDateValues(detached.id()).get(copiedField).get(date));
        assertEquals(
                "Auto", eventFieldRepo.findDateValues(series.id()).get(field).get(date.plusWeeks(1)));
        assertEquals(List.of(1), eventReminderRepo.findDays(detached.id()));
        assertTrue(eventReminderRepo.isSent(detached.id(), date, 1));
        assertEquals(
                detached.id(),
                attendanceRepo.findSessionById(sheet.id()).orElseThrow().eventId());

        var calendar = occurrenceCalendar.forStation(station.id());
        assertFalse(calendar.occursOn(series, date));
        assertTrue(calendar.occursOn(series, date.plusWeeks(1)));
        assertTrue(calendar.takesPlaceOn(detached, date));
    }

    /** A cancelled date taken out stays called off on the appointment that took it over. */
    @Test
    void aDetachedDateTakesItsCancellationAlong() {
        var series = weekly(null, null);
        LocalDate date = nextTuesday();
        eventDateCancellationRepo.cancel(series.id(), date, CancellationCause.MANUAL, "Sturm", null);

        var detached = track(scope.detach(series, date, edited(series, StationEvent.EventType.RECURRING)));

        assertTrue(eventDateCancellationRepo.isCancelled(detached.id(), date));
        assertFalse(eventDateCancellationRepo.isCancelled(series.id(), date));
    }

    /**
     * This and later with an end date: the old series ends the day before, the new one starts on the
     * date and keeps the end, and places are split along the date.
     */
    @Test
    void aSplitSeriesEndsTheDayBeforeAndGoesOnWithItsPlaces() {
        LocalDate end = nextTuesday().plusWeeks(5);
        var series = weekly(end, null);
        LocalDate from = nextTuesday().plusWeeks(1);
        var before = register(series, anna, nextTuesday());
        var after = register(series, ben, from.plusWeeks(1));
        var restrictions = new EventRestrictionService(eventRepo, restrictionService);
        restrictions.setRestrictions(
                series.id(),
                new RestrictionSelection(List.of(), List.of(), List.of(), List.of(anna.id()), RestrictionMode.OR));
        restrictions.updateRestrictionMode(series.id(), RestrictionMode.OR);

        var continued = track(scope.split(series, from, edited(series, StationEvent.EventType.RECURRING)));

        var old = eventRepo.findById(series.id()).orElseThrow();
        assertEquals(from.minusDays(1), old.repeatUntil());
        assertNull(old.repeatCount());
        assertEquals(end, continued.repeatUntil());
        assertEquals(StationEvent.EventType.RECURRING, continued.eventType());
        assertEquals(
                from,
                occurrenceCalendar
                        .forStation(station.id())
                        .ruleOf(continued)
                        .orElseThrow()
                        .first());
        assertEquals(
                series.id(),
                eventRegistrationRepo.findById(before.id()).orElseThrow().eventId());
        assertEquals(
                continued.id(),
                eventRegistrationRepo.findById(after.id()).orElseThrow().eventId());
        assertEquals(
                RegistrationStatus.ACCEPTED,
                eventRegistrationRepo.findById(after.id()).orElseThrow().status());
        assertEquals(RestrictionMode.OR, continued.restrictionMode());
        assertEquals(
                new EventLink(series.id(), "Dienst", from, true),
                eventRepo.findContinues(continued.id()).orElseThrow());
    }

    /** A series counted to an end keeps its total: the old one ends on a day, the new one has what is left. */
    @Test
    void aSplitSeriesCountedToAnEndKeepsItsTotal() {
        var series = weekly(null, 10);
        LocalDate from = nextTuesday().plusWeeks(1);
        int given = occurrenceCalendar
                .forStation(station.id())
                .between(series, series.startTime().atZone(zone).toLocalDate(), from.minusDays(1))
                .size();

        var continued = track(scope.split(series, from, edited(series, StationEvent.EventType.RECURRING)));

        assertEquals(10 - given, continued.repeatCount());
        assertNull(continued.repeatUntil());
        var old = eventRepo.findById(series.id()).orElseThrow();
        assertEquals(from.minusDays(1), old.repeatUntil());
        assertNull(old.repeatCount());
    }

    /** A date given away before stays given away when the series is later split ahead of it. */
    @Test
    void aSplitTakesTheDatesGivenAwayAlong() {
        var series = weekly(null, null);
        LocalDate given = nextTuesday().plusWeeks(2);
        var detached = track(scope.detach(series, given, edited(series, StationEvent.EventType.RECURRING)));

        var continued = track(scope.split(series, nextTuesday(), edited(series, StationEvent.EventType.RECURRING)));

        var calendar = occurrenceCalendar.forStation(station.id());
        assertFalse(calendar.occursOn(continued, given));
        assertTrue(calendar.occursOn(continued, given.plusWeeks(1)));
        assertTrue(calendar.takesPlaceOn(detached, given));
        assertEquals(
                continued.id(),
                eventRepo.findContinues(detached.id()).orElseThrow().eventId());
        assertEquals(
                List.of(continued.id()),
                eventRepo.findContinuations(series.id()).stream()
                        .map(EventLink::eventId)
                        .toList());
    }

    /** Sheets before the date stay with the old series, sheets from it on go with the new one. */
    @Test
    void aSplitTakesTheSheetsFromItsDateOn() {
        var series = weekly(null, null);
        LocalDate from = nextTuesday().plusWeeks(1);
        var template = attendanceRepo.createTemplate(station.id(), "Split Sheet");
        var before = series.occurrenceOn(nextTuesday(), zone).orElseThrow();
        var after = series.occurrenceOn(from.plusWeeks(2), zone).orElseThrow();
        var earlier =
                attendanceRepo.createSession(template.id(), before.start(), before.end(), series.id(), null, null);
        var later = attendanceRepo.createSession(template.id(), after.start(), after.end(), series.id(), null, null);

        var continued = track(scope.split(series, from, edited(series, StationEvent.EventType.RECURRING)));

        assertEquals(
                series.id(),
                attendanceRepo.findSessionById(earlier.id()).orElseThrow().eventId());
        assertEquals(
                continued.id(),
                attendanceRepo.findSessionById(later.id()).orElseThrow().eventId());
    }

    /** A date a break takes out still counts toward the total, the way a count is read everywhere. */
    @Test
    void aBreakBeforeTheSplitCountsTowardTheTotal() {
        var series = weekly(null, 10);
        LocalDate first = series.startTime().atZone(zone).toLocalDate();
        LocalDate from = nextTuesday().plusWeeks(1);
        var pause = eventBreakRepo.create(station.id(), "Ferien", first.plusWeeks(1), first.plusWeeks(1));
        int given = occurrenceCalendar
                        .forStation(station.id())
                        .between(series, first, from.minusDays(1))
                        .size()
                + 1;

        try {
            var continued = track(scope.split(series, from, edited(series, StationEvent.EventType.RECURRING)));

            assertEquals(10 - given, continued.repeatCount());
        } finally {
            eventBreakRepo.delete(pause.id());
        }
    }

    /** A draft naming both a last day and a number of dates is refused before anything is written. */
    @Test
    void aSplitWithBothEndsIsRefused() {
        var series = weekly(null, null);
        var draft = edited(series, StationEvent.EventType.RECURRING);
        var both = new EventDraft(
                draft.name(),
                null,
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
                nextTuesday().plusWeeks(8),
                4);

        assertRefused(
                EventRefusal.EVENT_SERIES_END_GIVEN_TWICE,
                () -> scope.split(series, nextTuesday().plusWeeks(1), both));
        assertTrue(eventRepo.findContinuations(series.id()).isEmpty());
    }

    /**
     * Saving names the dates: none means all of them, one date or one date and later need the date,
     * and the questions the editor saves right after with the ids of the originals land on the copies.
     */
    @Test
    void savingFollowsTheScopeAndKeepsTheCopiedQuestions() {
        var series = weekly(null, null);
        LocalDate date = nextTuesday();
        var draft = edited(series, StationEvent.EventType.RECURRING);
        var field = eventFieldRepo
                .create(
                        series.id(),
                        "Fahrer",
                        FieldType.TEXT,
                        new EventFieldConfig(null, null, null, null, true, null, true).settings(),
                        "",
                        0,
                        false,
                        null,
                        false)
                .id();
        eventFieldRepo.updateValueOn(field, date, "Bus");

        assertRefused(
                EventRefusal.EVENT_SCOPE_NEEDS_A_KIND, () -> scope.save(series, draft, new EditScope(null, date)));
        assertRefused(
                EventRefusal.EVENT_SCOPE_NEEDS_A_DATE,
                () -> scope.save(series, draft, new EditScope(EditScopeKind.THIS, null)));
        var detached = track(scope.save(series, draft, new EditScope(EditScopeKind.THIS, date)));
        var copy = eventFieldRepo.findByEvent(detached.id()).getFirst().id();
        eventFieldRepo.replaceFields(
                detached.id(),
                List.of(new EventFieldDraft(
                        field,
                        "Fahrer neu",
                        FieldType.TEXT,
                        new EventFieldConfig(null, null, null, null, true, null, true).settings(),
                        "",
                        false,
                        null,
                        false)));

        var saved = eventFieldRepo.findByEvent(detached.id());
        assertEquals(List.of(copy), saved.stream().map(f -> f.id()).toList());
        assertEquals(
                "Bus", eventFieldRepo.findDateValues(detached.id()).get(copy).get(date));
        assertEquals("Dienst neu", scope.save(series, draft, EditScope.ALL).name());
    }

    /** Edited from its very first date, a series is changed as a whole and no new one is made. */
    @Test
    void aSplitOnTheFirstDateChangesTheWholeSeries() {
        LocalDate first = nextTuesday();
        var series = weeklyFrom(first, null, null);

        var changed = scope.split(series, first, edited(series, StationEvent.EventType.RECURRING));

        assertEquals(series.id(), changed.id());
        assertEquals("Dienst neu", changed.name());
        assertTrue(eventRepo.findContinuations(series.id()).isEmpty());
    }

    /** A place on a date the continued series no longer falls on is withdrawn, as for any move. */
    @Test
    void aSplitIntoAnotherWeekdayWithdrawsThePlacesLeftBehind() {
        var series = weekly(null, null);
        LocalDate from = nextTuesday();
        var place = register(series, anna, from);
        var draft = edited(series, StationEvent.EventType.RECURRING);
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

        var continued = track(scope.split(series, from, onWednesday));

        var moved = eventRegistrationRepo.findById(place.id()).orElseThrow();
        assertEquals(continued.id(), moved.eventId());
        assertEquals(RegistrationStatus.WITHDRAWN, moved.status());
    }

    /** Past dates, dates the series does not fall on and one-off appointments are refused. */
    @Test
    void onlyAFutureDateOfASeriesCanBeEditedOnItsOwn() {
        var series = weekly(null, null);
        var draft = edited(series, StationEvent.EventType.RECURRING);
        LocalDate past = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY));

        assertRefused(EventRefusal.EVENT_SCOPE_DATE_PASSED, () -> scope.detach(series, past, draft));
        assertRefused(EventRefusal.EVENT_SCOPE_DATE_PASSED, () -> scope.split(series, past, draft));
        assertRefused(
                EventRefusal.EVENT_SCOPE_DATE_NOT_AN_OCCURRENCE,
                () -> scope.detach(series, nextTuesday().plusDays(1), draft));
        var oneOff = track(crud.create(
                station.id(),
                "Einmal",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                nextTuesday().atTime(18, 0).atZone(zone).toInstant(),
                nextTuesday().atTime(20, 0).atZone(zone).toInstant(),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null));
        assertRefused(EventRefusal.EVENT_SCOPE_NEEDS_A_SERIES, () -> scope.detach(oneOff, nextTuesday(), draft));
        assertRefused(
                EventRefusal.EVENT_SERIES_ENDS_BEFORE_IT_STARTS,
                () -> scope.split(
                        series,
                        nextTuesday().plusWeeks(1),
                        new EventDraft(
                                draft.name(),
                                null,
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
                                nextTuesday(),
                                null)));
        assertTrue(eventRepo.findContinuations(series.id()).isEmpty(), "a refused edit leaves nothing behind");
    }
}
