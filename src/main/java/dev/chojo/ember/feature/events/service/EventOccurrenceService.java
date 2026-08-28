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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.ZoneOffset;
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

    @Inject
    public EventOccurrenceService(EventCrudService eventCrudService, EventBreakService breakService) {
        this.eventCrudService = eventCrudService;
        this.breakService = breakService;
    }

    /**
     * Finds all events that occur today for a station, taking into account recurrence rules and break periods.
     * One-time events match by their start date; recurring events match by day of week and recurrence pattern.
     *
     * @param stationId the station ID
     * @return the list of today's events
     */
    public List<StationEvent> findTodayEvents(int stationId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        boolean inBreak = breakService.isDateInBreak(stationId, today);

        return eventCrudService.findByStation(stationId).stream()
                .filter(e -> occursToday(e, today, inBreak))
                .toList();
    }

    /**
     * Whether this event takes place today.
     *
     * <p>The recurrence itself is answered by the event, so that a series which has run its course is
     * over everywhere at once rather than in the places that remembered to ask.
     */
    private boolean occursToday(StationEvent event, LocalDate today, boolean inBreak) {
        if (event.eventType() == StationEvent.EventType.ONE_TIME) {
            if (event.startTime() == null) return false;
            LocalDate eventDateUtc = event.startTime().atZone(ZoneOffset.UTC).toLocalDate();
            return today.equals(eventDateUtc);
        }
        return !inBreak && event.occursOn(today);
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

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var occurrences = new ArrayList<UpcomingEventOccurrence>();

        for (var ev : events) {
            if (ev.eventType() != StationEvent.EventType.ONE_TIME || ev.startTime() == null) continue;
            LocalDate eventDate = ev.startTime().atZone(ZoneOffset.UTC).toLocalDate();
            if (!eventDate.isBefore(today)) {
                occurrences.add(new UpcomingEventOccurrence(EventSummary.of(ev), eventDate));
            }
        }

        for (int d = 0; d <= UPCOMING_DAYS; d++) {
            LocalDate date = today.plusDays(d);
            if (EventBreak.coversAny(breaks, date)) continue;

            for (var ev : events) {
                if (ev.occursOn(date)) {
                    occurrences.add(new UpcomingEventOccurrence(EventSummary.of(ev), date));
                }
            }
        }

        occurrences.sort(Comparator.comparing(UpcomingEventOccurrence::date));
        return occurrences.stream().skip(offset).limit(limit).toList();
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
