/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline.DeadlineAnchor;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The database places a deadline exactly where the server does, since lists and dashboards read the
 * close from SQL while sign-up is refused by the server. Checked across both changes of Berlin's
 * daylight saving time in 2026.
 */
class RegistrationClosesAtSqlTest extends RepositoryTestBase {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static Instant berlin(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant();
    }

    private static @Nullable Instant closesAtInSql(RegistrationDeadline deadline, Instant start) {
        var bound = RegistrationDeadlineSql.bind(call(), deadline)
                .bind("starts", start, INSTANT_TIMESTAMP)
                .bind("zone", BERLIN.getId());
        return query("""
                        SELECT registration_closes_at(
                                :registration_deadline_kind,
                                CAST(:registration_deadline AS TIMESTAMPTZ),
                                CAST(:registration_deadline_offset_minutes AS INT),
                                :registration_deadline_anchor,
                                CAST(:starts AS TIMESTAMPTZ),
                                :zone) AS closes_at;""")
                .single(bound)
                .map(row -> row.get("closes_at", INSTANT_TIMESTAMP))
                .first()
                .orElse(null);
    }

    @Test
    void theDatabaseClosesWhereTheServerDoes() {
        var week = 7 * 24 * 60;
        var rules = List.of(
                RegistrationDeadline.none(),
                RegistrationDeadline.fixed(berlin("2026-03-20T12:00")),
                RegistrationDeadline.relative(week, DeadlineAnchor.MIDNIGHT),
                RegistrationDeadline.relative(week, DeadlineAnchor.EXACT),
                RegistrationDeadline.relative(36 * 60, DeadlineAnchor.MIDNIGHT),
                RegistrationDeadline.relative(90, DeadlineAnchor.EXACT),
                RegistrationDeadline.relative(0, DeadlineAnchor.MIDNIGHT));
        var starts = List.of(
                berlin("2026-11-14T10:00"),
                berlin("2026-04-04T00:30"),
                berlin("2026-04-04T10:00"),
                berlin("2026-03-30T10:00"),
                berlin("2026-10-31T10:00"),
                berlin("2026-10-25T02:30"));

        for (var rule : rules) {
            for (var start : starts) {
                assertEquals(
                        rule.closesAt(start, BERLIN).orElse(null),
                        closesAtInSql(rule, start),
                        () -> rule + " for a start at " + start);
            }
        }
    }

    /**
     * The migration gives every notice and warning already recorded for a one-time appointment the close
     * {@code registration_closes_at} places from the stored rule, start and station clock. That has to be
     * the instant the sweep computes for the same date, or every migrated record would count as being
     * about an earlier close and be sent again.
     */
    @Test
    void theMigratedCloseIsTheOneTheSweepComputes() {
        var station = stationRepo.create("Closes At Backfill");
        stationRepo.updateTimezone(station.id(), BERLIN.getId());
        try {
            var zone = occurrenceCalendar.zoneOf(station.id());
            var start = berlin("2027-03-27T00:30");
            for (var rule : List.of(
                    RegistrationDeadline.fixed(berlin("2027-03-20T12:00")),
                    RegistrationDeadline.relative(7 * 24 * 60, DeadlineAnchor.MIDNIGHT),
                    RegistrationDeadline.relative(36 * 60, DeadlineAnchor.MIDNIGHT),
                    RegistrationDeadline.relative(90, DeadlineAnchor.EXACT))) {
                var event = eventRepo.create(
                        station.id(),
                        "Nachgetragen",
                        null,
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plusSeconds(7200),
                        null,
                        true,
                        rule,
                        false,
                        null,
                        null,
                        null,
                        null);
                try {
                    var backfilled = query("""
                                    SELECT registration_closes_at(e.registration_deadline_kind, e.registration_deadline,
                                                                  e.registration_deadline_offset_minutes,
                                                                  e.registration_deadline_anchor, e.start_time,
                                                                  s.timezone) AS closes_at
                                    FROM station_event e
                                             JOIN station s ON s.id = e.station_id
                                    WHERE e.id = :id;""")
                            .single(call().bind("id", event.id()))
                            .map(row -> row.get("closes_at", INSTANT_TIMESTAMP))
                            .first()
                            .orElse(null);
                    var date = LocalDate.ofInstant(event.startTime(), zone);
                    assertEquals(
                            event.registrationClosesOn(date, zone).orElse(null), backfilled, () -> rule.toString());
                } finally {
                    eventRepo.delete(event.id());
                }
            }
        } finally {
            stationRepo.delete(station.id());
        }
    }
}
