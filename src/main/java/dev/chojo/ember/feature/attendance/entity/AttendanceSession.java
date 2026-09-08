/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * An attendance session representing a specific occurrence where attendance is tracked.
 *
 * @param id         unique session identifier
 * @param templateId the template this session was created from
 * @param startTime  scheduled start time of the session
 * @param endTime    scheduled end time of the session
 * @param createdAt  timestamp when the session was created
 * @param eventId        optional linked event identifier
 * @param title          display title of the session
 * @param unlockedUntil  when a manager's reopening runs out, null where nobody reopened this sheet
 * @param lockedAt       when somebody closed this sheet by hand, null where nobody did
 * @param countedMinutes what a whole presence here is worth when hours are added up, null where the
 *     sheet's own times decide
 */
public record AttendanceSession(
        int id,
        int templateId,
        Instant startTime,
        Instant endTime,
        Instant createdAt,
        Integer eventId,
        String title,
        Instant unlockedUntil,
        Instant lockedAt,
        Integer countedMinutes) {

    /**
     * Whether the sheet may still be written to at the given moment.
     *
     * <p>Closing it by hand outranks everything, because closing a sheet somebody has just reopened
     * has to mean something. Otherwise a reopening that is still running keeps it open, and failing
     * both, its age against the configured span decides.
     *
     * @param now             the moment to judge by
     * @param freezeAfterDays how many days after its evening a sheet closes on its own
     * @return true where the sheet is open for writing
     */
    public boolean isOpen(Instant now, int freezeAfterDays) {
        if (lockedAt != null) return false;
        if (unlockedUntil != null && unlockedUntil.isAfter(now)) return true;
        return endTime.plus(Duration.ofDays(freezeAfterDays)).isAfter(now);
    }

    /**
     * What one member's presence from one moment to another is worth in hours.
     *
     * <p>The clock decides where the sheet says nothing else. Where it carries a number of its own,
     * that number is what a whole presence is worth and a shorter one counts its share of it, so an
     * evening of four hours counted as three gives three to whoever stayed and one and a half to
     * whoever left halfway. Nobody counts more than the sheet is worth, which is what stops an
     * arrival written before the sheet began from buying extra.
     *
     * @param from when the member arrived
     * @param to   when the member left
     * @return the hours this presence counts as, never negative
     */
    public double countedHours(Instant from, Instant to) {
        double hours = Duration.between(from, to).toMinutes() / 60.0;
        if (hours <= 0) return 0;
        if (countedMinutes == null) return hours;
        double span = Duration.between(startTime, endTime).toMinutes() / 60.0;
        double share = span > 0 ? Math.min(1.0, hours / span) : 1.0;
        return countedMinutes / 60.0 * share;
    }

    /**
     * Whether this sheet runs into a second calendar day.
     *
     * <p>Where it does, a time on its own cannot be read: Friday 20:00 and Saturday 20:00 are the
     * same four characters, and everything that shows or takes a time has to show its date as well.
     *
     * @param zone the station's timezone, which is where its days begin and end
     * @return true where the sheet ends on a later day than it starts
     */
    public boolean spansDays(ZoneId zone) {
        return !startTime.atZone(zone).toLocalDate().equals(endTime.atZone(zone).toLocalDate());
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AttendanceSession> map() {
        return row -> new AttendanceSession(
                row.getInt("id"),
                row.getInt("template_id"),
                row.get("start_time", INSTANT_TIMESTAMP),
                row.get("end_time", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("event_id", Integer.class),
                row.getString("title"),
                row.get("unlocked_until", INSTANT_TIMESTAMP),
                row.get("locked_at", INSTANT_TIMESTAMP),
                row.getObject("counted_minutes", Integer.class));
    }
}
