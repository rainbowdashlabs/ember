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
        Kind kind,
        @Nullable Instant at,
        @Nullable Integer offsetMinutes,
        @Nullable Anchor anchor) {

    private static final int MINUTES_PER_DAY = 1440;

    private static final RegistrationDeadline NONE = new RegistrationDeadline(Kind.NONE, null, null, null);

    /**
     * Normalises the rule, so only the values its kind uses are kept.
     */
    public RegistrationDeadline {
        if (kind == null) kind = Kind.NONE;
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
                if (anchor == null) anchor = Anchor.EXACT;
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
        return at == null ? NONE : new RegistrationDeadline(Kind.FIXED, at, null, null);
    }

    /**
     * A deadline a span before the start of each date.
     *
     * @param offsetMinutes how many minutes before the start
     * @param anchor        exact or down to midnight
     */
    public static RegistrationDeadline relative(int offsetMinutes, Anchor anchor) {
        return new RegistrationDeadline(Kind.RELATIVE, null, offsetMinutes, anchor);
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
                row.getEnum("registration_deadline_kind", Kind.class),
                row.get("registration_deadline", INSTANT_TIMESTAMP),
                row.getObject("registration_deadline_offset_minutes", Integer.class),
                row.getEnum("registration_deadline_anchor", Anchor.class));
    }

    /** Whether any deadline applies. */
    @JsonIgnore
    public boolean isSet() {
        return kind != Kind.NONE;
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
     * Whether sign-up for a date starting at a given instant has closed.
     *
     * @param start when the date starts
     * @param zone  the station's clock
     * @param now   the instant asked about
     * @return true once the deadline has been reached
     */
    public boolean closedAt(Instant start, ZoneId zone, Instant now) {
        return closesAt(start, zone).map(close -> !now.isBefore(close)).orElse(false);
    }

    /**
     * Places a relative deadline. A span of whole days at midnight counts calendar days on the
     * station's clock, so "7 days before, at midnight" stays the same day across a change of the
     * clocks; any other span is counted in minutes.
     */
    private Instant relativeTo(Instant start, ZoneId zone) {
        int minutes = offsetMinutes == null ? 0 : offsetMinutes;
        if (anchor == Anchor.MIDNIGHT && minutes % MINUTES_PER_DAY == 0) {
            return start.atZone(zone)
                    .toLocalDate()
                    .minusDays(minutes / MINUTES_PER_DAY)
                    .atStartOfDay(zone)
                    .toInstant();
        }
        var close = start.minus(Duration.ofMinutes(minutes));
        if (anchor != Anchor.MIDNIGHT) return close;
        return close.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    /** Which deadline applies. */
    public enum Kind {
        /** Sign-up never closes on its own. */
        NONE,
        /** Sign-up closes at one instant. */
        FIXED,
        /** Sign-up closes a span before the start of each date. */
        RELATIVE
    }

    /** How a relative deadline is placed. */
    public enum Anchor {
        /** Exactly the span before the start. */
        EXACT,
        /** At the midnight that begins the day the span lands on. */
        MIDNIGHT
    }
}
