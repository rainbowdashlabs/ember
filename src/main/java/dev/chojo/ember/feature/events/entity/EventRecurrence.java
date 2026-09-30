/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * How an appointment is written down for a calendar.
 *
 * <p>One place rather than one per feed: the station's own calendar file and the public one said the
 * same thing twice, so an appointment that runs out said it in the one that had been remembered and
 * repeated for ever in the other.
 *
 * <p>A series is written from the same {@link OccurrenceRule} the application reads its dates from.
 * It starts on its first date rather than on the day it was configured, because a calendar counts a
 * quarter from its start and always shows the start itself, and every date a break takes out is
 * named as an exception. A subscribed calendar therefore shows the dates the application shows.
 *
 * <p>A date called off is not an exception: it still belongs to the series, and a calendar is told
 * about it as an override of that one date, marked cancelled, so it shows the date crossed out and
 * every other date as it was.
 */
public final class EventRecurrence {

    private EventRecurrence() {}

    /** The identifier every entry of one appointment carries, the series and its overrides alike. */
    public static String uidOf(StationEvent event) {
        return "event-" + event.id() + "@ember";
    }

    /**
     * The override of a series for one date that was called off, marked cancelled. The dates to write
     * one for are {@link StationCalendar#cancelledDates}.
     *
     * @param event   the appointment
     * @param date    the date called off
     * @param summary the headline the override carries
     * @return the override, empty where the appointment has no times to place it at
     */
    public static Optional<VEvent> cancelledDateOf(StationEvent event, LocalDate date, String summary) {
        return event.occurrenceOn(date).map(span -> {
            var override = new VEvent(span.start(), span.end(), summary);
            override.add(new Uid(uidOf(event)));
            override.add(new RecurrenceId<>(span.start()));
            override.add(ImmutableStatus.VEVENT_CANCELLED);
            return override;
        });
    }

    /**
     * The calendar entry of an appointment.
     *
     * @param event    the appointment
     * @param summary  the headline the entry carries
     * @param calendar the station's calendar, which says where the series starts and what its breaks take out
     * @return the entry, empty for a series that falls on no date at all
     */
    public static Optional<VEvent> entryOf(StationEvent event, String summary, StationCalendar calendar) {
        if (!event.isRecurring()) {
            var start = event.startTime() != null ? event.startTime() : Instant.now();
            var end = event.endTime() != null ? event.endTime() : start;
            return Optional.of(new VEvent(start, end, summary));
        }
        return calendar.ruleOf(event).flatMap(rule -> seriesOf(event, summary, rule, calendar));
    }

    private static Optional<VEvent> seriesOf(
            StationEvent event, String summary, OccurrenceRule rule, StationCalendar calendar) {
        var first = event.occurrenceOn(rule.first());
        var calendarRule = rule.calendarRule();
        if (first.isEmpty() || calendarRule.isEmpty()) return Optional.empty();

        var vevent = new VEvent(first.get().start(), first.get().end(), summary);
        vevent.add(new RRule<>(calendarRule.get()));
        List<Instant> exceptions = calendar.suspendedDates(event).stream()
                .flatMap(date -> event.occurrenceOn(date).stream())
                .map(StationEvent.Span::start)
                .toList();
        if (!exceptions.isEmpty()) vevent.add(new ExDate<>(new DateList<>(exceptions)));
        return Optional.of(vevent);
    }
}
