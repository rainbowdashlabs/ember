/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.chojo.sadu.mapper.wrapper.Row;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * When sign-up closes: never, at one instant, or a span before the start of each date.
 *
 * <p>A fixed deadline closes every date of a series at once. A relative one closes each date on its
 * own, either exactly the span before its start, or at the midnight that begins the day the span
 * lands on, on the station's clock. "7 days before, at midnight" for a Saturday 10:00 start closes on
 * the Saturday a week earlier at 00:00.
 *
 * @param kind          which of the three applies
 * @param at            the instant of a fixed deadline, null for any other kind
 * @param offsetMinutes how many minutes before the start a relative deadline lies, null otherwise
 * @param anchor        how a relative deadline is placed, null otherwise
 */
public record RegistrationDeadline(
        DeadlineKind kind,
        @Nullable Instant at,
        @Nullable Integer offsetMinutes,
        @Nullable DeadlineAnchor anchor) {

    private static final int MINUTES_PER_DAY = 1440;

    private static final RegistrationDeadline NONE = new RegistrationDeadline(DeadlineKind.NONE, null, null, null);

    /**
     * Normalises the rule, so only the values its kind uses are kept.
     */
    public RegistrationDeadline {
        if (kind == null) kind = DeadlineKind.NONE;
        switch (kind) {
            case NONE -> {
                at = null;
                offsetMinutes = null;
                anchor = null;
            }
            case FIXED -> {
                offsetMinutes = null;
                anchor = null;
            }
            case RELATIVE -> {
                at = null;
                if (anchor == null) anchor = DeadlineAnchor.EXACT;
            }
        }
    }

    /** No deadline. */
    public static RegistrationDeadline none() {
        return NONE;
    }

    /**
     * A deadline at one instant.
     *
     * @param at the instant, or null for no deadline
     */
    public static RegistrationDeadline fixed(@Nullable Instant at) {
        return at == null ? NONE : new RegistrationDeadline(DeadlineKind.FIXED, at, null, null);
    }

    /**
     * A deadline a span before the start of each date.
     *
     * @param offsetMinutes how many minutes before the start
     * @param anchor        exact or down to midnight
     */
    public static RegistrationDeadline relative(int offsetMinutes, DeadlineAnchor anchor) {
        return new RegistrationDeadline(DeadlineKind.RELATIVE, null, offsetMinutes, anchor);
    }

    /**
     * The midnight a number of calendar days before the start, on the station's clock: the moment a
     * minimum of sign-ups is checked, for an appointment's date as for an event group's first part.
     *
     * @param days how many days before the start
     * @return the rule
     * @throws ArithmeticException where the days do not fit a span in minutes, which no stored minimum
     *                             reaches; the sweep that asked logs it
     */
    public static RegistrationDeadline daysBeforeAtMidnight(int days) {
        return relative(Math.multiplyExact(days, MINUTES_PER_DAY), DeadlineAnchor.MIDNIGHT);
    }

    /**
     * Reads the rule from the columns {@code registration_deadline},
     * {@code registration_deadline_kind}, {@code registration_deadline_offset_minutes} and
     * {@code registration_deadline_anchor}.
     *
     * @param row the row carrying them
     * @return the rule
     * @throws SQLException when a column cannot be read
     */
    public static RegistrationDeadline read(Row row) throws SQLException {
        return new RegistrationDeadline(
                row.getEnum("registration_deadline_kind", DeadlineKind.class),
                row.get("registration_deadline", INSTANT_TIMESTAMP),
                row.getObject("registration_deadline_offset_minutes", Integer.class),
                row.getEnum("registration_deadline_anchor", DeadlineAnchor.class));
    }

    /** Whether any deadline applies. */
    @JsonIgnore
    public boolean isSet() {
        return kind != DeadlineKind.NONE;
    }

    /** Whether the rule is valid: no negative span and a fixed deadline with an instant. */
    @JsonIgnore
    public boolean isValid() {
        return switch (kind) {
            case NONE -> true;
            case FIXED -> at != null;
            case RELATIVE -> offsetMinutes != null && offsetMinutes >= 0;
        };
    }

    /**
     * When sign-up closes for a date starting at a given instant.
     *
     * @param start when the date starts
     * @param zone  the station's clock, which places midnight
     * @return the instant sign-up closes, empty where no deadline applies
     */
    public Optional<Instant> closesAt(Instant start, ZoneId zone) {
        return switch (kind) {
            case NONE -> Optional.empty();
            case FIXED -> Optional.ofNullable(at);
            case RELATIVE -> Optional.of(relativeTo(start, zone));
        };
    }

    /**
     * Places a relative deadline. A span of whole days at midnight counts calendar days on the
     * station's clock, so "7 days before, at midnight" stays the same day across a change of the
     * clocks; any other span is counted in minutes.
     */
    private Instant relativeTo(Instant start, ZoneId zone) {
        int minutes = offsetMinutes == null ? 0 : offsetMinutes;
        if (anchor == DeadlineAnchor.MIDNIGHT && minutes % MINUTES_PER_DAY == 0) {
            return start.atZone(zone)
                    .toLocalDate()
                    .minusDays(minutes / MINUTES_PER_DAY)
                    .atStartOfDay(zone)
                    .toInstant();
        }
        var close = start.minus(Duration.ofMinutes(minutes));
        if (anchor != DeadlineAnchor.MIDNIGHT) return close;
        return close.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    /** Which deadline applies. */
    public enum DeadlineKind {
        /** Sign-up never closes on its own. */
        NONE,
        /** Sign-up closes at one instant. */
        FIXED,
        /** Sign-up closes a span before the start of each date. */
        RELATIVE
    }

    /** How a relative deadline is placed. */
    public enum DeadlineAnchor {
        /** Exactly the span before the start. */
        EXACT,
        /** At the midnight that begins the day the span lands on. */
        MIDNIGHT
    }
}
