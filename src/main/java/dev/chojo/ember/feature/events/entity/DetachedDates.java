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
import java.util.Set;
import java.util.TreeSet;

/**
 * The dates of a station's series that became appointments of their own, looked up by series.
 *
 * <p>A series skips such a date as though it did not fall on it: it is not called off, nobody holds
 * a place on it any more, and the appointment that took it over carries it instead.
 *
 * @param byEvent the dates taken out, per series
 */
public record DetachedDates(Map<Integer, Set<LocalDate>> byEvent) {

    /** No series gave a date away. */
    public static final DetachedDates NONE = new DetachedDates(Map.of());

    /**
     * The lookup over these rows.
     *
     * @param rows the dates taken out of the station's series
     * @return the lookup
     */
    public static DetachedDates of(Collection<EventDetachedDate> rows) {
        var byEvent = new HashMap<Integer, Set<LocalDate>>();
        for (var row : rows) {
            byEvent.computeIfAbsent(row.eventId(), id -> new TreeSet<>()).add(row.eventDate());
        }
        return new DetachedDates(byEvent);
    }

    /** Whether this date was taken out of this series. */
    public boolean on(int eventId, LocalDate date) {
        return byEvent.getOrDefault(eventId, Set.of()).contains(date);
    }

    /** Every date taken out of one series, earliest first. */
    public List<LocalDate> forEvent(int eventId) {
        return byEvent.getOrDefault(eventId, Set.of()).stream().sorted().toList();
    }
}
