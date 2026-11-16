/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.StationEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * When an occurrence of an appointment starts and ends.
 *
 * <p>An appointment carries the clock time of its first date and repeats that clock time onto every
 * date it falls on, so one occurrence is that date combined with the times read off the start and the
 * end. The reading is done on the station's clock, the one the calendar, the attendance sheets and the
 * identity of a sign-up read their days on. Read in UTC, a series configured in summer claimed its
 * gear an hour early all winter, and would have closed an invitation's date an hour early too.
 *
 * <p>The answers are instants rather than days on purpose: something running to Monday morning and
 * something starting Monday afternoon would otherwise collide over a Monday neither of them wants, as
 * gear held from Friday evening to Monday morning did.
 */
public final class OccurrenceWindows {

    private OccurrenceWindows() {}

    /**
     * When one occurrence of an appointment begins.
     *
     * @param event the appointment
     * @param date  the date
     * @param zone  the station's clock
     * @return the moment it begins
     */
    public static Instant startOf(StationEvent event, LocalDate date, ZoneId zone) {
        return date.atTime(timeOf(event.startTime(), LocalTime.MIN, zone))
                .atZone(zone)
                .toInstant();
    }

    /**
     * When one occurrence of an appointment ends.
     *
     * <p>An appointment whose end reads earlier in the day than its start runs past midnight, so the
     * end belongs to the following day.
     *
     * @param event the appointment
     * @param date  the date
     * @param zone  the station's clock
     * @return the moment it ends
     */
    public static Instant endOf(StationEvent event, LocalDate date, ZoneId zone) {
        LocalTime start = timeOf(event.startTime(), LocalTime.MIN, zone);
        LocalTime end = timeOf(event.endTime(), start, zone);
        LocalDate endDate = end.isBefore(start) ? date.plusDays(1) : date;
        return endDate.atTime(end).atZone(zone).toInstant();
    }

    /**
     * The day an appointment that happens once falls on.
     *
     * @param event the appointment
     * @param zone  the station's clock
     * @return the day, or {@code null} where it has no start
     */
    public static @Nullable LocalDate singleDateOf(StationEvent event, ZoneId zone) {
        if (event.startTime() == null) return null;
        return event.startTime().atZone(zone).toLocalDate();
    }

    private static LocalTime timeOf(@Nullable Instant instant, LocalTime fallback, ZoneId zone) {
        return instant == null ? fallback : instant.atZone(zone).toLocalTime();
    }
}
