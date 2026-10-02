/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dates of a station's appointments that are off, looked up by appointment and date.
 *
 * <p>Only dates that are off are held: a restored date is as good as never cancelled to everything
 * that reads the calendar.
 *
 * @param byEvent the dates that are off, per appointment
 */
public record DateCancellations(Map<Integer, Map<LocalDate, EventDateCancellation>> byEvent) {

    /** No date of any appointment is off. */
    public static final DateCancellations NONE = new DateCancellations(Map.of());

    /**
     * The lookup over these rows, leaving out the ones a manager restored.
     *
     * @param rows the cancellations read for a station
     * @return the lookup
     */
    public static DateCancellations of(Collection<EventDateCancellation> rows) {
        var byEvent = new HashMap<Integer, Map<LocalDate, EventDateCancellation>>();
        for (var row : rows) {
            if (!row.isActive()) continue;
            byEvent.computeIfAbsent(row.eventId(), id -> new HashMap<>()).put(row.eventDate(), row);
        }
        return new DateCancellations(byEvent);
    }

    /** The cancellation of one date of one appointment, empty where that date is not off. */
    public Optional<EventDateCancellation> on(int eventId, LocalDate date) {
        return Optional.ofNullable(byEvent.getOrDefault(eventId, Map.of()).get(date));
    }

    /** Every date of one appointment that is off, in no particular order. */
    public List<EventDateCancellation> forEvent(int eventId) {
        return List.copyOf(byEvent.getOrDefault(eventId, Map.of()).values());
    }
}
