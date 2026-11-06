/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
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
 * <p>A series is written on the station's clock, with the definition of that clock from
 * {@link #timeZoneOf} beside it, so a calendar repeats it at the same time of day on both sides of a
 * change of the clocks. Written in UTC, it repeated an hour off for half of the year.
 *
 * <p>A date called off is not an exception: it still belongs to the series, and a calendar is told
 * about it as an override of that one date, marked cancelled, so it shows the date crossed out and
 * every other date as it was.
 */
public final class EventRecurrence {

    private static final TimeZoneRegistry TIME_ZONES =
            TimeZoneRegistryFactory.getInstance().createRegistry();

    private EventRecurrence() {}

    /** The identifier every entry of one appointment carries, the series and its overrides alike. */
    public static String uidOf(StationEvent event) {
        return "event-" + event.id() + "@ember";
    }

    /**
     * The definition of the station's clock a calendar needs to read the entries written on it.
     *
     * @param zone the station's clock
     * @return the definition, empty for a clock that is a fixed offset and needs none
     */
    public static Optional<VTimeZone> timeZoneOf(ZoneId zone) {
        if (zone instanceof ZoneOffset) return Optional.empty();
        var timeZone = TIME_ZONES.getTimeZone(zone.getId());
        return Optional.ofNullable(timeZone).map(TimeZone::getVTimeZone);
    }

    /**
     * The override of a series for one date that was called off, marked cancelled. The dates to write
     * one for are {@link StationCalendar#cancelledDates}.
     *
     * @param event    the appointment
     * @param date     the date called off
     * @param summary  the headline the override carries
     * @param calendar the station's calendar, whose clock the series is written on
     * @return the override, empty where the appointment has no times to place it at
     */
    public static Optional<VEvent> cancelledDateOf(
            StationEvent event, LocalDate date, String summary, StationCalendar calendar) {
        var zone = calendar.zone();
        return event.occurrenceOn(date, zone).map(span -> {
            var override = new VEvent(onClock(span.start(), zone), onClock(span.end(), zone), summary);
            override.add(new Uid(uidOf(event)));
            override.add(new RecurrenceId<>(onClock(span.start(), zone)));
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
        var zone = calendar.zone();
        var first = event.occurrenceOn(rule.first(), zone);
        var calendarRule = rule.calendarRule();
        if (first.isEmpty() || calendarRule.isEmpty()) return Optional.empty();

        var vevent = new VEvent(
                onClock(first.get().start(), zone), onClock(first.get().end(), zone), summary);
        vevent.add(new RRule<>(calendarRule.get()));
        List<Temporal> exceptions = calendar.suspendedDates(event).stream()
                .flatMap(date -> event.occurrenceOn(date, zone).stream())
                .map(span -> onClock(span.start(), zone))
                .toList();
        if (!exceptions.isEmpty()) vevent.add(exceptionsOf(exceptions, zone));
        return Optional.of(vevent);
    }

    /**
     * A moment as a calendar is to read it: in UTC on a clock that is a fixed offset, and otherwise
     * as the time of day on the station's clock, which is what a series repeats.
     */
    private static Temporal onClock(Instant moment, ZoneId zone) {
        return zone instanceof ZoneOffset ? moment : moment.atZone(zone);
    }

    /**
     * The dates a series skips. A list of times on the station's clock has to name that clock
     * itself, since ical4j writes it as a floating time otherwise and a calendar reads it on its own.
     */
    private static ExDate<Temporal> exceptionsOf(List<Temporal> exceptions, ZoneId zone) {
        var dates = new DateList<>(exceptions);
        if (zone instanceof ZoneOffset) return new ExDate<>(dates);
        return new ExDate<>(new ParameterList(List.of(new TzId(zone.getId()))), dates);
    }
}
