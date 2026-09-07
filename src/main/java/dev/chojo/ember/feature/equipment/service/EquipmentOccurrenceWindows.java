/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.feature.events.entity.StationEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * When an evening of an appointment starts and ends, and how long the gear it needs is away.
 *
 * <p>An appointment carries the clock time of its first date and repeats that clock time onto every
 * date it falls on, so one evening is that date combined with the times read off the start and the
 * end. The reading is done in UTC because every other date derivation in the appointment code is:
 * the calendar, the reminders and the identity of a sign-up all read the day off the start time that
 * way, and a claim that computed the day differently would hold a window on a different evening than
 * the one the sign-ups are keyed to.
 *
 * <p>A window is instants rather than days on purpose. Lead and trail are "Friday evening to Monday
 * morning", and on days alone a claim running to Monday morning and one starting Monday afternoon
 * would collide over a Monday neither of them wants.
 */
public final class EquipmentOccurrenceWindows {

    private EquipmentOccurrenceWindows() {}

    /**
     * When one evening of an appointment begins.
     *
     * @param event the appointment
     * @param date  the evening
     * @return the moment it begins
     */
    public static Instant startOf(StationEvent event, LocalDate date) {
        return date.atTime(timeOf(event.startTime(), LocalTime.MIN)).toInstant(ZoneOffset.UTC);
    }

    /**
     * When one evening of an appointment ends.
     *
     * <p>An appointment whose end reads earlier in the day than its start runs past midnight, so the
     * end belongs to the following day.
     *
     * @param event the appointment
     * @param date  the evening
     * @return the moment it ends
     */
    public static Instant endOf(StationEvent event, LocalDate date) {
        LocalTime start = timeOf(event.startTime(), LocalTime.MIN);
        LocalTime end = timeOf(event.endTime(), start);
        LocalDate endDate = end.isBefore(start) ? date.plusDays(1) : date;
        return endDate.atTime(end).toInstant(ZoneOffset.UTC);
    }

    /**
     * The day an appointment that happens once falls on.
     *
     * @param event the appointment
     * @return the day, or {@code null} where it has no start
     */
    public static LocalDate singleDateOf(StationEvent event) {
        if (event.startTime() == null) return null;
        return event.startTime().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static LocalTime timeOf(Instant instant, LocalTime fallback) {
        return instant == null ? fallback : instant.atZone(ZoneOffset.UTC).toLocalTime();
    }
}
