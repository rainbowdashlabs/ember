/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import dev.chojo.ember.feature.events.entity.EventRecurrence;
import dev.chojo.ember.feature.events.entity.StationEvent;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * What every calendar feed shares, whoever it is for: the window of appointments it carries and the
 * frame around its entries.
 */
public final class CalendarFeeds {
    /**
     * Past calendar window: a year back, the same distance the feed reaches forward. A calendar is
     * as much a record of what happened as a plan of what is coming.
     */
    private static final Duration WINDOW_PAST = Duration.ofDays(365);

    /** Forward calendar window: covers annual events without unbounded growth on long-running stations. */
    private static final Duration WINDOW_FUTURE = Duration.ofDays(365);

    private CalendarFeeds() {}

    /**
     * Whether an appointment falls within a year either way of now, so feeds stay small for stations
     * with thousands of past entries. A series is kept by its anchor: its rule expands across the window
     * in the client.
     *
     * @param event the appointment
     * @param now   the moment the window is centred on
     * @return whether a calendar feed carries it
     */
    public static boolean inWindow(StationEvent event, Instant now) {
        if (event.isRecurring()) return true;
        Instant start = event.startTime();
        return start != null && !start.isBefore(now.minus(WINDOW_PAST)) && !start.isAfter(now.plus(WINDOW_FUTURE));
    }

    /**
     * An empty calendar with the frame every feed carries: product, version, scale, its name and the
     * station's time zone.
     *
     * @param product what kind of calendar it is, part of its product identifier
     * @param name    the name calendar apps show for it
     * @param zone    the station's time zone
     * @return the calendar, ready for its entries
     */
    public static Calendar frame(String product, String name, ZoneId zone) {
        var calendar = new Calendar();
        calendar.add(new ProdId("-//Ember//" + product + "//DE"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(new XProperty("X-WR-CALNAME", name));
        EventRecurrence.timeZoneOf(zone).ifPresent(calendar::add);
        return calendar;
    }
}
