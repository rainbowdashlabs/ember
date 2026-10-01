/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.DateCancellations;
import dev.chojo.ember.feature.events.entity.OccurrenceRule;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventDateCancellationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which days appointments fall on, for everything that has to name one.
 *
 * <p>Every place that needs an occurrence asks here: the lists and the calendar, the reminders, the
 * registration deadlines, registering itself, the exports and the calendar feed. The answer is a
 * {@link StationCalendar}, which holds the station's clock and its breaks, so a caller going through
 * many appointments of one station reads both once.
 *
 * <p>It reads nothing but repositories on purpose. Every renderer, feed and export needs a date, and
 * a calendar that pulled in the services those things already sit inside would close a circle.
 */
@Singleton
public class OccurrenceCalendar {

    /**
     * How far ahead a list of occurrences looks before it stops.
     *
     * <p>Past the next date of anything that repeats at all, yearly included, and near enough that a
     * page nobody can fill is still cheap to ask for.
     */
    public static final int LOOKAHEAD_DAYS = 1100;

    private final EventRepository eventRepository;
    private final EventBreakRepository breakRepository;
    private final EventDateCancellationRepository cancellationRepository;
    private final StationRepository stationRepository;

    @Inject
    public OccurrenceCalendar(
            EventRepository eventRepository,
            EventBreakRepository breakRepository,
            EventDateCancellationRepository cancellationRepository,
            StationRepository stationRepository) {
        this.eventRepository = eventRepository;
        this.breakRepository = breakRepository;
        this.cancellationRepository = cancellationRepository;
        this.stationRepository = stationRepository;
    }

    /** The calendar of one station: its clock, its breaks and the dates called off, read now. */
    public StationCalendar forStation(int stationId) {
        return new StationCalendar(
                zoneOf(stationId),
                breakRepository.findByStation(stationId),
                DateCancellations.of(cancellationRepository.findActiveByStation(stationId)));
    }

    /** The clock the station keeps its days by, which is the one its appointments are read on. */
    public ZoneId zoneOf(int stationId) {
        return StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
    }

    /**
     * The day a registration or a decline is filed against, refused where the appointment does not
     * take place on it.
     *
     * <p>A one-off appointment has its own day and the one asked for does not matter. A series needs
     * the day named, and that day has to be one the series really falls on: the right weekday is not
     * enough for a series on the first of a month, and neither is a day past the end of the series or
     * inside a break of the station. Either way the day must not have been called off, which a whole
     * series called off counts as.
     *
     * @param event     the appointment
     * @param requested the day asked for, on the station's clock, or null where none was named
     * @return the day to file the answer against
     */
    public LocalDate dateToAnswerFor(StationEvent event, @Nullable LocalDate requested) {
        var calendar = forStation(event.stationId());
        LocalDate date = event.isRecurring() ? requireNamed(requested) : firstDateOf(event, calendar);
        return switch (calendar.check(event, date)) {
            case OCCURRENCE -> date;
            case NOT_AN_OCCURRENCE -> throw EventRefusal.REGISTRATION_DAY_NOT_AN_OCCURRENCE.raise();
            case IN_A_BREAK -> throw EventRefusal.REGISTRATION_DAY_IN_A_BREAK.raise();
            case CANCELLED -> throw EventRefusal.REGISTRATION_DAY_CANCELLED.raise();
        };
    }

    private static LocalDate requireNamed(@Nullable LocalDate requested) {
        if (requested == null) throw EventRefusal.REGISTRATION_NEEDS_A_DAY.raise();
        return requested;
    }

    private static LocalDate firstDateOf(StationEvent event, StationCalendar calendar) {
        return calendar.ruleOf(event)
                .map(OccurrenceRule::first)
                .orElseThrow(EventRefusal.EVENT_HAS_NO_START_TIME::raise);
    }

    /**
     * The next date an appointment takes place on, today counting as next.
     *
     * @return the date, empty where none is left
     */
    public Optional<LocalDate> next(StationEvent event) {
        var calendar = forStation(event.stationId());
        return calendar.next(event, calendar.today());
    }

    /**
     * The date a screen about one appointment is read for: the next one, today counting as next, and
     * the last one it had once none is left, so a page about a past appointment still shows what it
     * showed on the day.
     *
     * @return the date, or nothing for an appointment that falls on no date at all
     */
    public Optional<LocalDate> dateInView(StationEvent event) {
        return forStation(event.stationId()).dateInView(event);
    }

    /** The same for an appointment read by its id, for callers that hold nothing but the id. */
    public Optional<LocalDate> dateInView(int eventId) {
        return eventRepository.findById(eventId).flatMap(this::dateInView);
    }

    /** The date in view of each of these appointments, leaving out the ones that have none. */
    public Map<Integer, LocalDate> datesInView(List<StationEvent> events) {
        var calendars = new HashMap<Integer, StationCalendar>();
        var dates = new HashMap<Integer, LocalDate>();
        for (var event : events) {
            calendars
                    .computeIfAbsent(event.stationId(), this::forStation)
                    .dateInView(event)
                    .ifPresent(date -> dates.put(event.id(), date));
        }
        return dates;
    }

    /**
     * The dates of an appointment worth looking at from today up to a horizon.
     *
     * <p>A one-off appointment has its own date whether it has been or not, because what hangs off
     * that date still belongs to it afterwards.
     *
     * @param event the appointment
     * @param days  how far ahead to look
     */
    public List<LocalDate> occurrencesWithin(StationEvent event, int days) {
        var calendar = forStation(event.stationId());
        if (!event.isRecurring()) {
            return calendar.ruleOf(event).map(rule -> List.of(rule.first())).orElse(List.of());
        }
        LocalDate today = calendar.today();
        return calendar.between(event, today, today.plusDays(days));
    }
}
