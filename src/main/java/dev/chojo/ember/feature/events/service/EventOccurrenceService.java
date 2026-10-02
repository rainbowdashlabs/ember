/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.DatedEvent;
import dev.chojo.ember.feature.events.entity.EventSummary;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.UpcomingEventOccurrence;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Lists the concrete dates events take place on, a page at a time.
 *
 * <p>Which dates those are is the {@link OccurrenceCalendar}'s answer, breaks included. This only
 * filters the appointments, merges their dates into one list and cuts the page out of it.
 */
@Singleton
public class EventOccurrenceService {
    private final EventCrudService eventCrudService;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public EventOccurrenceService(EventCrudService eventCrudService, OccurrenceCalendar occurrenceCalendar) {
        this.eventCrudService = eventCrudService;
        this.occurrenceCalendar = occurrenceCalendar;
    }

    /**
     * Finds all events that take place today for a station, on the station's own clock.
     *
     * @param stationId the station ID
     * @return the list of today's events
     */
    public List<StationEvent> findTodayEvents(int stationId) {
        var calendar = occurrenceCalendar.forStation(stationId);
        LocalDate today = calendar.today();
        return eventCrudService.findByStation(stationId).stream()
                .filter(event -> calendar.occursOn(event, today))
                .toList();
    }

    /**
     * The next page of occurrences, earliest first.
     *
     * <p>The appointments' dates are merged the way the calendar would read them, one date after the
     * other, and the walk stops as soon as the page is full. An appointment that comes round once a
     * quarter is therefore on the list as soon as it is among the next dates, however far ahead that
     * is, and a first page of ten is ten found and no more.
     *
     * @param stationId the station the list belongs to
     * @param memberIds the members the caller may see appointments for, null where that is everybody
     * @param query     the filters, the window of days and the page asked for
     * @return the occurrences of that page, earliest first
     */
    public List<UpcomingEventOccurrence> findUpcomingOccurrences(
            int stationId, @Nullable List<Integer> memberIds, OccurrenceQuery query) {
        var events = matchingEvents(stationId, memberIds, query);
        if (events.isEmpty()) return List.of();

        var calendar = occurrenceCalendar.forStation(stationId);
        LocalDate first = notBefore(calendar.today(), query.from());
        LocalDate last = notAfter(first.plusDays(OccurrenceCalendar.LOOKAHEAD_DAYS), query.to());

        var walk = new Walk(
                (event, day) -> calendar.next(event, day),
                day -> day.plusDays(1),
                day -> !day.isAfter(last),
                Comparator.naturalOrder());
        return page(walk.from(first, events, query.offset() + query.limit(), calendar), query);
    }

    /**
     * The page of occurrences that have already happened, latest first.
     *
     * <p>The same walk as {@link #findUpcomingOccurrences} with the dates taken the other way. Going
     * back there is a real floor and no guess is needed: nothing falls before the first date of its
     * series.
     *
     * <p>Days come newest first, and within a day the appointments read in the order they ran, which
     * is the order every other list of a day shows them in.
     *
     * @param stationId the station the list belongs to
     * @param memberIds the members the caller may see appointments for, null where that is everybody
     * @param query     the filters, the window of days and the page asked for
     * @return the occurrences of that page, latest first
     */
    public List<UpcomingEventOccurrence> findPastOccurrences(
            int stationId, @Nullable List<Integer> memberIds, OccurrenceQuery query) {
        var events = matchingEvents(stationId, memberIds, query);
        if (events.isEmpty()) return List.of();

        var calendar = occurrenceCalendar.forStation(stationId);
        LocalDate newest = notAfter(calendar.today().minusDays(1), query.to());
        LocalDate oldest = query.from();

        var walk = new Walk(
                (event, day) -> calendar.previous(event, day.plusDays(1)),
                day -> day.minusDays(1),
                day -> oldest == null || !day.isBefore(oldest),
                Comparator.reverseOrder());
        return page(walk.from(newest, events, query.offset() + query.limit(), calendar), query);
    }

    /**
     * A page of appointments themselves rather than of their occurrences, each placed against a date.
     *
     * <p>An appointment is current while it still has a next date and past once it has none, which is
     * deliberately not the same question as whether it has occurrences behind it. A weekly drill that
     * has run for years is current here on the very day it fills a page of past occurrences, because
     * it still comes round; a one-off is past the day after it ran.
     *
     * <p>Current appointments are ordered by the date they next fall on and past ones by the date
     * they last fell on, so both tabs read from the date nearest to now outwards. Every row carries
     * both dates whichever tab it came from, because a list ordered by a date it never shows is a
     * column of names in no order the reader can see.
     *
     * @param stationId the station the list belongs to
     * @param memberIds the members the caller may see appointments for, null where that is everybody
     * @param query     which half and which kind of appointment, with the filters and the page
     * @return the appointments of that page
     */
    public List<DatedEvent> findEventsPage(int stationId, @Nullable List<Integer> memberIds, EventPageQuery query) {
        var filter = query.filter();
        EventKind kind = query.kind();
        var events = matchingEvents(stationId, memberIds, filter).stream()
                .filter(ev -> kind == null || kind.covers(ev))
                .toList();
        if (events.isEmpty()) return List.of();

        var calendar = occurrenceCalendar.forStation(stationId);
        LocalDate today = calendar.today();
        boolean past = query.state() == EventState.PAST;

        var placed = new ArrayList<PlacedEvent>();
        for (var event : events) {
            Optional<LocalDate> next = calendar.next(event, today);
            if (next.isPresent() == past) continue;
            Optional<LocalDate> previous = calendar.previous(event, today);
            LocalDate on = past ? previous.orElse(null) : next.get();
            if (!withinWindow(on, filter)) continue;
            placed.add(new PlacedEvent(event, on, next.orElse(null), previous.orElse(null)));
        }
        placed.sort(byDate(past).thenComparing(entry -> entry.event().id()));

        return placed.stream()
                .skip(filter.offset())
                .limit(filter.limit())
                .map(entry -> new DatedEvent(
                        EventSummary.of(entry.event()),
                        entry.next(),
                        entry.previous(),
                        entry.on() == null
                                ? null
                                : calendar.noticeOn(entry.event(), entry.on()).orElse(null)))
                .toList();
    }

    /**
     * The order a tab reads in: soonest first while there is still something to come, newest first
     * once there is not. An appointment whose date could not be worked out at all sorts to the end.
     */
    private static Comparator<PlacedEvent> byDate(boolean past) {
        Comparator<LocalDate> order = past
                ? Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())
                : Comparator.nullsLast(Comparator.<LocalDate>naturalOrder());
        return Comparator.comparing(PlacedEvent::on, order);
    }

    /** Whether a date falls inside the window the query asked for, where it asked for one at all. */
    private static boolean withinWindow(@Nullable LocalDate date, OccurrenceQuery query) {
        LocalDate from = query.from();
        LocalDate to = query.to();
        if (from == null && to == null) return true;
        if (date == null) return false;
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    /**
     * Everything falling on one date, in the order the list shows a day's appointments, each saying
     * whether it is off on that date.
     */
    private static List<UpcomingEventOccurrence> onDate(
            List<StationEvent> events, LocalDate date, StationCalendar calendar) {
        var onThisDate = new ArrayList<UpcomingEventOccurrence>();
        for (var ev : events) {
            onThisDate.add(new UpcomingEventOccurrence(
                    EventSummary.of(ev), date, calendar.noticeOn(ev, date).orElse(null)));
        }
        onThisDate.sort(Comparator.comparing((UpcomingEventOccurrence o) -> timeOfDay(o, calendar.zone()))
                .thenComparing(occurrence -> occurrence.event().id()));
        return onThisDate;
    }

    /** The given date held at or after a floor, where the caller named one. */
    private static LocalDate notBefore(LocalDate date, @Nullable LocalDate floor) {
        return floor == null || floor.isBefore(date) ? date : floor;
    }

    /** The given date held at or before a ceiling, where the caller named one. */
    private static LocalDate notAfter(LocalDate date, @Nullable LocalDate ceiling) {
        return ceiling == null || ceiling.isAfter(date) ? date : ceiling;
    }

    /** The slice of a walk's result the caller asked for. */
    private static List<UpcomingEventOccurrence> page(List<UpcomingEventOccurrence> found, OccurrenceQuery query) {
        return found.stream().skip(query.offset()).limit(query.limit()).toList();
    }

    /**
     * When in the day an occurrence starts, which is what orders the several that share a date.
     *
     * <p>A repeating event carries the clock time of its first date rather than of this one, and
     * that clock time is the same on every date it repeats onto, so reading it off the start is
     * right for both kinds. It is read on the station's clock, which is the one the day beside it was
     * worked out on and the one a reader of the list sees. An event with no start time at all sorts
     * to the top of its day.
     */
    private static LocalTime timeOfDay(UpcomingEventOccurrence occurrence, ZoneId zone) {
        Instant start = occurrence.event().startTime();
        return start == null ? LocalTime.MIN : start.atZone(zone).toLocalTime();
    }

    private List<StationEvent> matchingEvents(int stationId, @Nullable List<Integer> memberIds, OccurrenceQuery query) {
        var events = eventCrudService.findFilteredForMembers(
                stationId, memberIds, query.categoryId(), query.requiresRegistration());
        String asked = query.search();
        if (asked == null || asked.isBlank()) return events;

        String search = asked.toLowerCase();
        return events.stream()
                .filter(ev -> {
                    String name = ev.name() != null ? ev.name().toLowerCase() : "";
                    String description = ev.description();
                    String desc = description != null ? description.toLowerCase() : "";
                    return name.contains(search) || desc.contains(search);
                })
                .toList();
    }

    /**
     * One direction of travel through the calendar: how each appointment finds its next date that way,
     * how to step past a date, how far the walk may go, and which date comes first.
     *
     * <p>The appointments are merged one date at a time, so the walk ends as soon as the page is full
     * and never looks further than the page needs.
     *
     * @param dateFrom the first date on or beyond a day, in this direction, that an appointment falls on
     * @param beyond   the day just past a day, in this direction
     * @param reaches  whether the walk may still take a date
     * @param order    which of two dates comes first in this direction
     */
    private record Walk(
            BiFunction<StationEvent, LocalDate, Optional<LocalDate>> dateFrom,
            UnaryOperator<LocalDate> beyond,
            Predicate<LocalDate> reaches,
            Comparator<LocalDate> order) {

        /** The occurrences from a day on, whole days at a time until at least this many are found. */
        List<UpcomingEventOccurrence> from(
                LocalDate start, List<StationEvent> events, int wanted, StationCalendar calendar) {
            var queue = new PriorityQueue<Cursor>(Comparator.comparing(Cursor::date, order));
            for (var event : events) advance(event, start, queue);

            var found = new ArrayList<UpcomingEventOccurrence>();
            while (!queue.isEmpty() && found.size() < wanted) {
                LocalDate day = queue.peek().date();
                var onDay = new ArrayList<StationEvent>();
                while (!queue.isEmpty() && queue.peek().date().equals(day)) {
                    var cursor = queue.poll();
                    onDay.add(cursor.event());
                    advance(cursor.event(), beyond.apply(day), queue);
                }
                found.addAll(onDate(onDay, day, calendar));
            }
            return found;
        }

        private void advance(StationEvent event, LocalDate day, PriorityQueue<Cursor> queue) {
            dateFrom.apply(event, day).filter(reaches).ifPresent(date -> queue.add(new Cursor(event, date)));
        }
    }

    /** Where one appointment stands in a walk: the next date it has in the walk's direction. */
    private record Cursor(StationEvent event, LocalDate date) {}

    /**
     * What a listing was asked for: which appointments to consider, which stretch of days to look at
     * and which slice of the answer to hand back.
     *
     * @param categoryId           the category to keep, null for all of them
     * @param requiresRegistration whether to keep only the ones that must be answered, null for all
     * @param search               free text matched against name and description, null for all
     * @param from                 the earliest date to consider, null where the list starts where it
     *                             naturally starts
     * @param to                   the latest date to consider, null where it ends where it naturally
     *                             ends
     * @param limit                how many entries the page holds
     * @param offset               how many entries to pass over before the page begins
     */
    public record OccurrenceQuery(
            @Nullable Integer categoryId,
            @Nullable Boolean requiresRegistration,
            @Nullable String search,
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            int limit,
            int offset) {}

    /**
     * What a page of appointments was asked for, which is a listing plus the two splits a page of
     * appointments has that a page of occurrences does not.
     *
     * @param state  whether the appointments still to come or the ones behind us are wanted
     * @param kind   which kind of appointment to keep, null for both of them
     * @param filter the filters, the window of days and the page
     */
    public record EventPageQuery(EventState state, @Nullable EventKind kind, OccurrenceQuery filter) {}

    /** Which half of the appointments a page asks for. */
    public enum EventState {
        CURRENT,
        PAST
    }

    /**
     * Which kind of appointment a page asks for.
     *
     * <p>The two are listed apart because they read apart: a one-off belongs in a list ordered by
     * date, a series belongs in a block of its own. The split happens here rather than in the
     * browser, because a page of ten split after the fact is four rows in one list and six in the
     * other, and asking for more of either then means asking for more of both.
     */
    public enum EventKind {
        ONE_TIME,
        REPEATING;

        /** Whether an appointment is of this kind. */
        public boolean covers(StationEvent event) {
            return event.isRecurring() == (this == REPEATING);
        }
    }

    /**
     * An appointment with both of its dates and the one its page is ordered by, which is not the
     * same one on both lists.
     *
     * @param event    the appointment
     * @param on       the date the page sorts by
     * @param next     the first date from today on that it falls on, null where it has none left
     * @param previous the last date before today that it fell on, null where it has yet to run
     */
    private record PlacedEvent(StationEvent event, LocalDate on, LocalDate next, LocalDate previous) {}
}
