/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

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
     * over everywhere at once rather than in the places that remembered to ask.
     */
    private boolean occursToday(StationEvent event, LocalDate today, boolean inBreak, ZoneId zone) {
        if (event.eventType() == StationEvent.EventType.ONE_TIME) {
            if (event.startTime() == null) return false;
            return today.equals(event.startTime().atZone(zone).toLocalDate());
        }
        return !inBreak && event.occursOn(today, zone);
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
     */
    public List<UpcomingEventOccurrence> findUpcomingOccurrences(
            int stationId,
            List<Integer> memberIds,
            Integer categoryId,
            Boolean requiresRegistration,
            String search,
            int limit,
            int offset) {
        var events = matchingEvents(stationId, memberIds, categoryId, requiresRegistration, search);
        if (events.isEmpty()) return List.of();

        var breaks = breakService.findByStation(stationId);
        var zone = timezoneOf(stationId);
        LocalDate today = LocalDate.now(zone);
        LocalDate lastWorthAsking = lastDateWorthAsking(events, zone, today);

        int wanted = offset + limit;
        var occurrences = new ArrayList<UpcomingEventOccurrence>();
        for (LocalDate date = today;
                !date.isAfter(lastWorthAsking) && occurrences.size() < wanted;
                date = date.plusDays(1)) {
            if (EventBreak.coversAny(breaks, date)) continue;
            occurrences.addAll(onDate(events, date, zone));
        }
        return occurrences.stream().skip(offset).limit(limit).toList();
    }

    /** Everything falling on one date, in the order the list shows a day's appointments. */
    private List<UpcomingEventOccurrence> onDate(List<StationEvent> events, LocalDate date, ZoneId zone) {
        var onThisDate = new ArrayList<UpcomingEventOccurrence>();
        for (var ev : events) {
            boolean falls = ev.eventType() == StationEvent.EventType.ONE_TIME
                    ? ev.startTime() != null
                            && ev.startTime().atZone(zone).toLocalDate().equals(date)
                    : ev.occursOn(date, zone);
            if (falls) onThisDate.add(new UpcomingEventOccurrence(EventSummary.of(ev), date));
        }
        onThisDate.sort(Comparator.comparing((UpcomingEventOccurrence o) -> timeOfDay(o, zone))
                .thenComparing(occurrence -> occurrence.event().id()));
        return onThisDate;
    }

    /**
     * The last date the walk could still find anything on, so a page that cannot be filled ends
     * rather than counting days for ever.
     *
     * <p>Where every appointment has an end, that is the latest of them. Where any repeats without
     * one there is no such date, and {@link #MAX_LOOKAHEAD_DAYS} stands in: far enough that a yearly
     * appointment is reached, and near enough that asking for a page nobody can fill is still cheap.
     */
    private static LocalDate lastDateWorthAsking(List<StationEvent> events, ZoneId zone, LocalDate today) {
        LocalDate furthest = today;
        for (var ev : events) {
            if (ev.eventType() == StationEvent.EventType.ONE_TIME) {
                if (ev.startTime() == null) continue;
                LocalDate on = ev.startTime().atZone(zone).toLocalDate();
                if (on.isAfter(furthest)) furthest = on;
                continue;
            }
            Optional<LocalDate> ends = ev.lastDate();
            if (ends.isEmpty()) return today.plusDays(MAX_LOOKAHEAD_DAYS);
            if (ends.get().isAfter(furthest)) furthest = ends.get();
        }
        return furthest;
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

    private List<StationEvent> matchingEvents(
            int stationId, List<Integer> memberIds, Integer categoryId, Boolean requiresRegistration, String search) {
        var events = eventCrudService.findFilteredForMembers(stationId, memberIds, categoryId, requiresRegistration);
        if (search == null || search.isBlank()) return events;

        String query = search.toLowerCase();
        return events.stream()
                .filter(ev -> {
                    String name = ev.name() != null ? ev.name().toLowerCase() : "";
                    String desc = ev.description() != null ? ev.description().toLowerCase() : "";
                    return name.contains(query) || desc.contains(query);
                })
                .toList();
    }
}
