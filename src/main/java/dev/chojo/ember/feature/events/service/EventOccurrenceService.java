/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.DatedEvent;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventSummary;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.UpcomingEventOccurrence;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Expands the recurrence rules of events into the concrete dates they take place on, honouring the
 * break periods of the station.
 */
@Singleton
public class EventOccurrenceService {
    /**
     * How far the walk goes before it gives up on filling a page.
     *
     * <p>Only reached where an appointment repeats with no end and the page still could not be
     * filled, which means the station has very little on. Three years is past the next date of
     * anything that repeats at all, yearly included.
     */
    private static final int MAX_LOOKAHEAD_DAYS = 1100;

    private final EventCrudService eventCrudService;
    private final EventBreakService breakService;
    private final StationRepository stationRepository;

    @Inject
    public EventOccurrenceService(
            EventCrudService eventCrudService, EventBreakService breakService, StationRepository stationRepository) {
        this.eventCrudService = eventCrudService;
        this.breakService = breakService;
        this.stationRepository = stationRepository;
    }

    /**
     * The clock a station keeps its days by.
     *
     * <p>Which day it is, and which day an appointment falls on, are both the station's to answer. A
     * server an hour or two behind is still on yesterday late in the evening, so asking it left
     * today's appointment off that list and put tomorrow's one day early.
     *
     * @param stationId the station whose day is meant
     * @return its timezone, UTC where it keeps none
     */
    private ZoneId timezoneOf(int stationId) {
        return StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
    }

    /**
     * Finds all events that occur today for a station, taking into account recurrence rules and break periods.
     * One-time events match by their start date; recurring events match by day of week and recurrence pattern.
     *
     * @param stationId the station ID
     * @return the list of today's events
     */
    public List<StationEvent> findTodayEvents(int stationId) {
        var zone = timezoneOf(stationId);
        LocalDate today = LocalDate.now(zone);
        boolean inBreak = breakService.isDateInBreak(stationId, today);

        return eventCrudService.findByStation(stationId).stream()
                .filter(e -> occursToday(e, today, inBreak, zone))
                .toList();
    }

    /**
     * Whether this event takes place today.
     *
     * <p>The recurrence itself is answered by the event, so that a series which has run its course is
     * over everywhere at once rather than in the places that remembered to ask. A break suspends a
     * series and leaves a one-off appointment standing, which is what a break is for.
     */
    private static boolean occursToday(StationEvent event, LocalDate today, boolean inBreak, ZoneId zone) {
        if (inBreak && event.isRecurring()) return false;
        return fallsOn(event, today, zone);
    }

    /**
     * The next page of occurrences, worked out one day at a time until the page is full.
     *
     * <p>Asked day by day the way the calendar asks it, rather than by expanding a fixed stretch of
     * time and slicing what comes out. The stretch used to be four weeks, which is generous for a
     * weekly appointment and blind to every rarer one: an appointment that comes round once a
     * quarter had no date inside the window and so never reached the list at all, while the calendar
     * found it as soon as somebody paged forward to its month.
     *
     * <p>Walking instead of expanding also means the work follows the page rather than the calendar:
     * a first page of ten is ten found and no more, however far ahead the tenth turns out to be.
     *
     * @param stationId the station the list belongs to
     * @param memberIds the members the caller may see appointments for, null where that is everybody
     * @param query     the filters, the window of days and the page asked for
     * @return the occurrences of that page, earliest first
     */
    public List<UpcomingEventOccurrence> findUpcomingOccurrences(
            int stationId, List<Integer> memberIds, OccurrenceQuery query) {
        var events = matchingEvents(stationId, memberIds, query);
        if (events.isEmpty()) return List.of();

        var breaks = breakService.findByStation(stationId);
        var zone = timezoneOf(stationId);
        LocalDate first = notBefore(LocalDate.now(zone), query.from());
        LocalDate last = notAfter(lastDateWorthAsking(events, zone, first), query.to());

        int wanted = query.offset() + query.limit();
        var occurrences = new ArrayList<UpcomingEventOccurrence>();
        for (LocalDate date = first; !date.isAfter(last) && occurrences.size() < wanted; date = date.plusDays(1)) {
            if (EventBreak.coversAny(breaks, date)) continue;
            occurrences.addAll(onDate(events, date, zone));
        }
        return page(occurrences, query);
    }

    /**
     * The page of occurrences that have already happened, worked out one day at a time going back.
     *
     * <p>The same walk as {@link #findUpcomingOccurrences} with the day stepping the other way,
     * rather than a second idea about what an occurrence is: the same recurrence rules answer the
     * same question about a date, and a break removes a date going back exactly as it does going
     * forward. Only the two bounds differ. Forwards the walk is bounded by how far anybody pages,
     * because a series without an end never runs out; backwards there is a real floor and no guess is
     * needed, since an appointment cannot have happened before it was written.
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
            int stationId, List<Integer> memberIds, OccurrenceQuery query) {
        var events = matchingEvents(stationId, memberIds, query);
        if (events.isEmpty()) return List.of();

        var breaks = breakService.findByStation(stationId);
        var zone = timezoneOf(stationId);
        Optional<LocalDate> oldestWritten = firstDateWorthAsking(events, zone);
        if (oldestWritten.isEmpty()) return List.of();

        LocalDate newest = notAfter(LocalDate.now(zone).minusDays(1), query.to());
        LocalDate oldest = notBefore(oldestWritten.get(), query.from());

        int wanted = query.offset() + query.limit();
        var occurrences = new ArrayList<UpcomingEventOccurrence>();
        for (LocalDate date = newest; !date.isBefore(oldest) && occurrences.size() < wanted; date = date.minusDays(1)) {
            if (EventBreak.coversAny(breaks, date)) continue;
            occurrences.addAll(onPastDate(events, date, zone));
        }
        return page(occurrences, query);
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
    public List<DatedEvent> findEventsPage(int stationId, List<Integer> memberIds, EventPageQuery query) {
        var filter = query.filter();
        var events = matchingEvents(stationId, memberIds, filter).stream()
                .filter(ev -> query.kind() == null || query.kind().covers(ev))
                .toList();
        if (events.isEmpty()) return List.of();

        var breaks = breakService.findByStation(stationId);
        var zone = timezoneOf(stationId);
        LocalDate today = LocalDate.now(zone);
        boolean past = query.state() == EventState.PAST;

        var placed = new ArrayList<PlacedEvent>();
        for (var event : events) {
            Optional<LocalDate> next = nextDate(event, breaks, zone, today);
            if (next.isPresent() == past) continue;
            Optional<LocalDate> previous = previousDate(event, breaks, zone, today);
            LocalDate on = past ? previous.orElse(null) : next.get();
            if (!withinWindow(on, filter)) continue;
            placed.add(new PlacedEvent(event, on, next.orElse(null), previous.orElse(null)));
        }
        placed.sort(byDate(past).thenComparing(entry -> entry.event().id()));

        return placed.stream()
                .skip(filter.offset())
                .limit(filter.limit())
                .map(entry -> new DatedEvent(EventSummary.of(entry.event()), entry.next(), entry.previous()))
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
    private static boolean withinWindow(LocalDate date, OccurrenceQuery query) {
        if (query.from() == null && query.to() == null) return true;
        if (date == null) return false;
        return (query.from() == null || !date.isBefore(query.from()))
                && (query.to() == null || !date.isAfter(query.to()));
    }

    /**
     * The first date from today on that an appointment falls on.
     *
     * <p>This is what says whether an appointment is current at all, and it is worked out by the same
     * walk that builds a list of occurrences rather than by a second reading of the recurrence rules:
     * a break that removes the next date moves the appointment to the one after it, here as
     * everywhere else.
     *
     * @param event the appointment
     * @return the next date it falls on, empty where it has none left
     */
    public Optional<LocalDate> findNextDate(StationEvent event) {
        var zone = timezoneOf(event.stationId());
        return nextDate(event, breakService.findByStation(event.stationId()), zone, LocalDate.now(zone));
    }

    /** The first date from today on that one appointment falls on, for a caller that holds the breaks already. */
    private static Optional<LocalDate> nextDate(
            StationEvent event, List<EventBreak> breaks, ZoneId zone, LocalDate today) {
        LocalDate last = lastDateWorthAsking(List.of(event), zone, today);
        for (LocalDate date = today; !date.isAfter(last); date = date.plusDays(1)) {
            if (EventBreak.coversAny(breaks, date)) continue;
            if (fallsOn(event, date, zone)) return Optional.of(date);
        }
        return Optional.empty();
    }

    /**
     * The last date before today that one appointment fell on, which is what orders the past by
     * recency.
     *
     * <p>Started from the last date the appointment could possibly fall on rather than from
     * yesterday, so that a series which ended three years ago is found in a few steps instead of a
     * thousand.
     */
    private static Optional<LocalDate> previousDate(
            StationEvent event, List<EventBreak> breaks, ZoneId zone, LocalDate today) {
        Optional<LocalDate> written = writtenOn(event, zone);
        if (written.isEmpty()) return Optional.empty();

        LocalDate newest = notAfter(today.minusDays(1), lastPossibleDate(event, zone));
        for (LocalDate date = newest; !date.isBefore(written.get()); date = date.minusDays(1)) {
            if (EventBreak.coversAny(breaks, date)) continue;
            if (fallsOn(event, date, zone)) return Optional.of(date);
        }
        return Optional.empty();
    }

    /** Everything falling on one date, in the order the list shows a day's appointments. */
    private static List<UpcomingEventOccurrence> onDate(List<StationEvent> events, LocalDate date, ZoneId zone) {
        var onThisDate = new ArrayList<UpcomingEventOccurrence>();
        for (var ev : events) {
            if (fallsOn(ev, date, zone)) onThisDate.add(new UpcomingEventOccurrence(EventSummary.of(ev), date));
        }
        onThisDate.sort(Comparator.comparing((UpcomingEventOccurrence o) -> timeOfDay(o, zone))
                .thenComparing(occurrence -> occurrence.event().id()));
        return onThisDate;
    }

    /**
     * The same for a date that has gone by, where an appointment only counts once it existed.
     *
     * <p>A repeating appointment answers for its weekday on every date there has ever been: the rule
     * says which days it lands on and nothing in it says when it started. Going forward that never
     * shows, because today is after the day it was written. Going back it would hand out a weekly
     * drill every week since the epoch, so the day it was written is the floor.
     */
    private static List<UpcomingEventOccurrence> onPastDate(List<StationEvent> events, LocalDate date, ZoneId zone) {
        return onDate(events.stream().filter(ev -> existedOn(ev, date, zone)).toList(), date, zone);
    }

    /**
     * Whether an appointment falls on a date, by its own date where it carries one and by its
     * recurrence rule otherwise.
     */
    private static boolean fallsOn(StationEvent event, LocalDate date, ZoneId zone) {
        if (event.eventType() == StationEvent.EventType.ONE_TIME) {
            return writtenOn(event, zone).filter(date::equals).isPresent();
        }
        return event.occursOn(date, zone);
    }

    /** Whether the appointment had been written down by this date, which is what bounds a walk back. */
    private static boolean existedOn(StationEvent event, LocalDate date, ZoneId zone) {
        return writtenOn(event, zone).filter(written -> !date.isBefore(written)).isPresent();
    }

    /**
     * The day an appointment was written, read on the station's clock.
     *
     * <p>Nothing records a creation date of its own and nothing needs to: the start an appointment
     * carries is the day somebody first configured it, and a repeating one keeps that day unchanged
     * while its dates move on around it.
     *
     * @return the day it was written, empty where it carries no date at all
     */
    private static Optional<LocalDate> writtenOn(StationEvent event, ZoneId zone) {
        return Optional.ofNullable(event.startTime())
                .map(start -> start.atZone(zone).toLocalDate());
    }

    /**
     * The last date one appointment could possibly fall on, where anything bounds it at all.
     *
     * @return that date, or null where the appointment repeats without an end
     */
    private static LocalDate lastPossibleDate(StationEvent event, ZoneId zone) {
        if (event.eventType() == StationEvent.EventType.ONE_TIME) {
            return writtenOn(event, zone).orElse(null);
        }
        return event.lastDate().orElse(null);
    }

    /**
     * The last date the walk could still find anything on, so a page that cannot be filled ends
     * rather than counting days for ever.
     *
     * <p>Where every appointment has an end, that is the latest of them. Where any repeats without
     * one there is no such date, and {@link #MAX_LOOKAHEAD_DAYS} stands in: far enough that a yearly
     * appointment is reached, and near enough that asking for a page nobody can fill is still cheap.
     */
    private static LocalDate lastDateWorthAsking(List<StationEvent> events, ZoneId zone, LocalDate from) {
        LocalDate furthest = from;
        for (var ev : events) {
            if (ev.eventType() == StationEvent.EventType.ONE_TIME) {
                Optional<LocalDate> on = writtenOn(ev, zone);
                if (on.isPresent() && on.get().isAfter(furthest)) furthest = on.get();
                continue;
            }
            Optional<LocalDate> ends = ev.lastDate();
            if (ends.isEmpty()) return from.plusDays(MAX_LOOKAHEAD_DAYS);
            if (ends.get().isAfter(furthest)) furthest = ends.get();
        }
        return furthest;
    }

    /**
     * The earliest date the walk back could still find anything on, which is the day the oldest of
     * these appointments was written.
     *
     * @return that day, empty where not one of them carries a date and so none of them has a past
     */
    private static Optional<LocalDate> firstDateWorthAsking(List<StationEvent> events, ZoneId zone) {
        return events.stream()
                .map(ev -> writtenOn(ev, zone))
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder());
    }

    /** The given date held at or after a floor, where the caller named one. */
    private static LocalDate notBefore(LocalDate date, LocalDate floor) {
        return floor == null || floor.isBefore(date) ? date : floor;
    }

    /** The given date held at or before a ceiling, where the caller named one. */
    private static LocalDate notAfter(LocalDate date, LocalDate ceiling) {
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

    private List<StationEvent> matchingEvents(int stationId, List<Integer> memberIds, OccurrenceQuery query) {
        var events = eventCrudService.findFilteredForMembers(
                stationId, memberIds, query.categoryId(), query.requiresRegistration());
        if (query.search() == null || query.search().isBlank()) return events;

        String search = query.search().toLowerCase();
        return events.stream()
                .filter(ev -> {
                    String name = ev.name() != null ? ev.name().toLowerCase() : "";
                    String desc = ev.description() != null ? ev.description().toLowerCase() : "";
                    return name.contains(search) || desc.contains(search);
                })
                .toList();
    }

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
            Integer categoryId,
            Boolean requiresRegistration,
            String search,
            LocalDate from,
            LocalDate to,
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
    public record EventPageQuery(EventState state, EventKind kind, OccurrenceQuery filter) {}

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
