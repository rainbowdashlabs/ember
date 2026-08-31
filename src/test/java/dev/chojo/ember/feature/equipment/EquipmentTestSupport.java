/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment;

import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * The appointments the equipment tests plan against, written straight to the repository.
 *
 * <p>Every one of them is anchored to a fixed date rather than to today, so a test that walks a
 * window says which evenings it expects instead of depending on the day it runs.
 */
public final class EquipmentTestSupport {

    /** A Saturday, so a weekly series and a one-off can both be pinned to the same weekday. */
    public static final LocalDate SATURDAY = LocalDate.of(2026, 6, 6);

    private EquipmentTestSupport() {}

    /**
     * An appointment that happens once, from 09:00 to 17:00 on the given day.
     *
     * @param repository the event repository
     * @param stationId  the station it belongs to
     * @param name       what it is called
     * @param date       the day it falls on
     * @return the appointment
     */
    public static StationEvent oneOff(EventRepository repository, int stationId, String name, LocalDate date) {
        return repository.create(
                stationId,
                name,
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                at(date, 9),
                at(date, 17),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * A weekly appointment on the weekday of the given day, from 19:00 to 21:00.
     *
     * @param repository the event repository
     * @param stationId  the station it belongs to
     * @param name       what it is called
     * @param first      the first day it falls on
     * @return the appointment
     */
    public static StationEvent weekly(EventRepository repository, int stationId, String name, LocalDate first) {
        return repository.create(
                stationId,
                name,
                "",
                StationEvent.EventType.RECURRING,
                first.getDayOfWeek().getValue(),
                at(first, 19),
                at(first, 21),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * A moment on a day, in the reading the appointment code uses everywhere.
     *
     * @param date the day
     * @param hour the hour
     * @return the moment
     */
    public static Instant at(LocalDate date, int hour) {
        return date.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC);
    }
}
