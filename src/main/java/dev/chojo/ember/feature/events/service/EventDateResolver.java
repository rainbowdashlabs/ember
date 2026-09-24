/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which days an appointment falls on, for everything that has to name one.
 *
 * <p>An answer given per date, a registration and an attendance sheet all belong to a day, and most
 * of the screens and feeds that read them are showing one occurrence without having been told which.
 * This says which: the next one, today counting as next.
 *
 * <p>It reads nothing but repositories on purpose. Every renderer, feed and export needs a date, and
 * a resolver that pulled in the services those things already sit inside would close a circle.
 */
@Singleton
public class EventDateResolver {

    /**
     * How far the walk looks before giving up.
     *
     * <p>Past the next date of anything that repeats at all, yearly included, and near enough that
     * asking about an appointment whose series has run out is still cheap.
     */
    private static final int MAX_LOOKAHEAD_DAYS = 1100;

    private final EventRepository eventRepository;
    private final EventBreakRepository breakRepository;
    private final StationRepository stationRepository;

    @Inject
    public EventDateResolver(
            EventRepository eventRepository,
            EventBreakRepository breakRepository,
            StationRepository stationRepository) {
        this.eventRepository = eventRepository;
        this.breakRepository = breakRepository;
        this.stationRepository = stationRepository;
    }

    /** The clock the station keeps its days by, which is the one its appointments are read on. */
    public ZoneId zoneOf(int stationId) {
        return StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
    }

    /**
     * The next date one appointment falls on, today counting as next.
     *
     * <p>A one-off keeps its own date whether it has been or not, and a series that has run out
     * keeps its last, so a screen showing a past appointment still shows what it showed on the day.
     *
     * @return the date, or nothing for an appointment that has no date at all
     */
    public Optional<LocalDate> nextDate(StationEvent event) {
        var zone = zoneOf(event.stationId());
        LocalDate today = LocalDate.now(zone);
        if (!event.isRecurring()) {
            return Optional.ofNullable(event.startTime())
                    .map(start -> start.atZone(zone).toLocalDate());
        }
        var breaks = breakRepository.findByStation(event.stationId());
        LocalDate until = event.lastDate().orElse(today.plusDays(MAX_LOOKAHEAD_DAYS));
        for (LocalDate date = today; !date.isAfter(until); date = date.plusDays(1)) {
            if (EventBreak.coversAny(breaks, date)) continue;
            if (event.occursOn(date, zone)) return Optional.of(date);
        }
        return event.lastDate();
    }

    /** The next date of an appointment read by its id, for callers that hold nothing but the id. */
    public Optional<LocalDate> nextDate(int eventId) {
        return eventRepository.findById(eventId).flatMap(this::nextDate);
    }

    /** The next date of each of these appointments, leaving out the ones that have none. */
    public Map<Integer, LocalDate> nextDates(List<StationEvent> events) {
        var dates = new HashMap<Integer, LocalDate>();
        for (var event : events) {
            nextDate(event).ifPresent(date -> dates.put(event.id(), date));
        }
        return dates;
    }

    /**
     * Every date an appointment falls on from today up to a horizon, the station's breaks left out.
     *
     * @param event the appointment
     * @param days  how far ahead to look
     */
    public List<LocalDate> occurrencesWithin(StationEvent event, int days) {
        var zone = zoneOf(event.stationId());
        LocalDate today = LocalDate.now(zone);
        if (!event.isRecurring()) {
            if (event.startTime() == null) return List.of();
            return List.of(event.startTime().atZone(zone).toLocalDate());
        }
        var breaks = breakRepository.findByStation(event.stationId());
        LocalDate horizon = today.plusDays(days);
        LocalDate until =
                event.lastDate().filter(last -> last.isBefore(horizon)).orElse(horizon);
        var dates = new ArrayList<LocalDate>();
        for (LocalDate date = today; !date.isAfter(until); date = date.plusDays(1)) {
            if (EventBreak.coversAny(breaks, date)) continue;
            if (event.occursOn(date, zone)) dates.add(date);
        }
        return dates;
    }
}
