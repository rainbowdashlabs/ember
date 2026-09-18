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

/**
 * Expands the recurrence rules of events into the concrete dates they take place on, honouring the
 * break periods of the station.
 */
@Singleton
public class EventOccurrenceService {
    private static final int UPCOMING_DAYS = 28;

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
     * server an hour or two behind is still on yesterday late in the evening, so asking it left this
     * evening's appointment off the list of today's and put tomorrow's one day early.
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
     * Expands events into chronologically sorted date occurrences for the next 28 days,
     * applying optional server-side filters, with pagination on the expanded list.
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
        var breaks = breakService.findByStation(stationId);

        var zone = timezoneOf(stationId);
        LocalDate today = LocalDate.now(zone);
        var occurrences = new ArrayList<UpcomingEventOccurrence>();

        for (var ev : events) {
            if (ev.eventType() != StationEvent.EventType.ONE_TIME || ev.startTime() == null) continue;
            LocalDate eventDate = ev.startTime().atZone(zone).toLocalDate();
            if (!eventDate.isBefore(today)) {
                occurrences.add(new UpcomingEventOccurrence(EventSummary.of(ev), eventDate));
            }
        }

        for (int d = 0; d <= UPCOMING_DAYS; d++) {
            LocalDate date = today.plusDays(d);
            if (EventBreak.coversAny(breaks, date)) continue;

            for (var ev : events) {
                if (ev.occursOn(date, zone)) {
                    occurrences.add(new UpcomingEventOccurrence(EventSummary.of(ev), date));
                }
            }
        }

        occurrences.sort(Comparator.comparing(UpcomingEventOccurrence::date)
                .thenComparing(occurrence -> timeOfDay(occurrence, zone))
                .thenComparing(occurrence -> occurrence.event().id()));
        return occurrences.stream().skip(offset).limit(limit).toList();
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
