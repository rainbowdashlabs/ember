/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * The dates of appointments whose sign-up is still open and closes within a window: what the closing
 * warnings warn about and what the list of answers still owed shows.
 *
 * <p>A relative deadline closes each date on its own, so every date close enough to close in time is
 * a candidate. A fixed deadline closes every date at once; only the next date counts, since what is
 * asked is to answer before it closes. A date called off or taken out by a break is left out, and so
 * are parts of an event group, which close with their event group.
 */
@Singleton
public class ClosingDates {
    private final EventRepository eventRepository;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public ClosingDates(EventRepository eventRepository, OccurrenceCalendar occurrenceCalendar) {
        this.eventRepository = eventRepository;
        this.occurrenceCalendar = occurrenceCalendar;
    }

    /**
     * The dates of every station whose sign-up closes after one instant and no later than another.
     *
     * @param from  the earliest close, excluded
     * @param until the latest close, included
     * @return one entry per appointment and date
     */
    public List<ClosingDate> between(Instant from, Instant until) {
        var calendars = new HashMap<Integer, StationCalendar>();
        var closing = new ArrayList<ClosingDate>();
        for (var event : eventRepository.findWithDeadline()) {
            var calendar = calendars.computeIfAbsent(event.stationId(), occurrenceCalendar::forStation);
            closing.addAll(of(event, calendar, from, until));
        }
        return closing;
    }

    /**
     * The dates of one appointment whose sign-up closes after one instant and no later than another.
     *
     * @param event    the appointment
     * @param calendar its station's calendar
     * @param from     the earliest close, excluded
     * @param until    the latest close, included
     * @return its dates in order
     */
    public static List<ClosingDate> of(StationEvent event, StationCalendar calendar, Instant from, Instant until) {
        var closing = new ArrayList<ClosingDate>();
        for (var date : candidateDates(event, calendar, until)) {
            var closes = event.registrationClosesOn(date, calendar.zone());
            if (closes.isEmpty() || !closes.get().isAfter(from) || closes.get().isAfter(until)) continue;
            closing.add(new ClosingDate(event, date, closes.get()));
        }
        return closing;
    }

    /**
     * Every date of a series with a fixed deadline up to a last day, while that deadline is still
     * ahead. The one instant closes them all, so each of them is still open until then; the closing
     * warnings ask only about the next one, but somebody who answered it still owes the others.
     *
     * @param event    the series
     * @param calendar its station's calendar
     * @param from     the instant the deadline has to lie after
     * @param lastDay  the last date asked about
     * @return its open dates in order, each closing at the fixed deadline
     */
    public static List<ClosingDate> openDatesOfFixedSeries(
            StationEvent event, StationCalendar calendar, Instant from, LocalDate lastDay) {
        var at = event.registrationDeadline().at();
        if (at == null || !at.isAfter(from)) return List.of();
        return calendar.between(event, calendar.today(), lastDay).stream()
                .filter(date -> calendar.takesPlaceOn(event, date))
                .map(date -> new ClosingDate(event, date, at))
                .toList();
    }

    private static List<LocalDate> candidateDates(StationEvent event, StationCalendar calendar, Instant limit) {
        var today = calendar.today();
        var deadline = event.registrationDeadline();
        if (deadline.kind() == RegistrationDeadline.DeadlineKind.FIXED) {
            return calendar.next(event, today)
                    .filter(date -> calendar.takesPlaceOn(event, date))
                    .map(List::of)
                    .orElse(List.of());
        }
        long spanDays = Duration.ofMinutes(Objects.requireNonNullElse(deadline.offsetMinutes(), 0))
                        .toDays()
                + 1;
        var last = limit.atZone(calendar.zone()).toLocalDate().plusDays(spanDays);
        return calendar.between(event, today, last).stream()
                .filter(date -> calendar.takesPlaceOn(event, date))
                .toList();
    }

    /**
     * One date of an appointment whose sign-up closes in the window asked about.
     *
     * @param event  the appointment
     * @param date   the date
     * @param closes when sign-up for that date closes
     */
    public record ClosingDate(StationEvent event, LocalDate date, Instant closes) {}
}
