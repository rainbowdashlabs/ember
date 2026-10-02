/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The part of the migration that moves cancellation onto dates and the minimum-registration deadline
 * onto days before each date.
 *
 * <p>The schema of this class is already migrated, so the old shape is put back by hand, filled the
 * way the old code filled it, and the part of the patch that does this work is run over it once more.
 * Every test class has a schema of its own, so taking the shape back touches no other test.
 */
class EventDateCancellationMigrationTest extends RepositoryTestBase {
    private static final String PATCH = "database/postgresql/1/patch_77.sql";
    private static final String FIRST_STATEMENT = "CREATE TABLE ember_schema.event_date_cancellation";
    private static final String LAST_STATEMENT = "COMMENT ON COLUMN ember_schema.station_event.threshold_days";

    private static Station station;
    private static int oneOffCancelled;
    private static int oneOffCancelledByThreshold;
    private static int seriesCancelled;
    private static int oneOffWithThreshold;
    private static int oneOffThresholdAfterStart;
    private static int seriesWithThreshold;

    @BeforeAll
    static void migrateTheOldShape() throws IOException, SQLException {
        station = stationRepo.create("Cancellation Migration");
        execute("""
                DROP TABLE event_date_cancellation;
                ALTER TABLE station_event
                    DROP COLUMN threshold_days,
                    ADD COLUMN threshold_date TIMESTAMPTZ,
                    ADD COLUMN threshold_notified BOOLEAN NOT NULL DEFAULT FALSE;""");

        oneOffCancelled = oldEvent("ONE_TIME", "2027-03-10T23:30:00Z", true, "Sturm", null, null);
        oneOffCancelledByThreshold = oldEvent(
                "ONE_TIME", "2027-03-11T08:00:00Z", true, "Mindestanzahl von 5 Anmeldungen nicht erreicht", null, 5);
        seriesCancelled = oldEvent("RECURRING", "2027-03-10T08:00:00Z", true, "Aufgelöst", null, null);
        oneOffWithThreshold = oldEvent("ONE_TIME", "2027-03-10T08:00:00Z", false, null, "2027-03-07T23:30:00Z", 4);
        oneOffThresholdAfterStart =
                oldEvent("ONE_TIME", "2027-03-10T08:00:00Z", false, null, "2027-03-12T08:00:00Z", 4);
        seriesWithThreshold = oldEvent("RECURRING", "2027-03-10T08:00:00Z", false, null, "2027-03-01T08:00:00Z", 6);

        execute(conversionPart());
    }

    /** A cancelled one-off keeps its reason on the date it falls on in the station's own time zone. */
    @Test
    void aCancelledOneOffBecomesACancelledDate() {
        var row = cancellation(oneOffCancelled).orElseThrow();
        assertEquals(LocalDate.parse("2027-03-11"), row.date(), "23:30 UTC is already the next day in Berlin");
        assertEquals("MANUAL", row.cause());
        assertEquals("Sturm", row.reason());
        assertFalse(seriesFlag(oneOffCancelled));
    }

    /** The sentence the old check wrote is recognised and turned back into its cause. */
    @Test
    void theOldAutomaticSentenceBecomesTheThresholdCause() {
        var row = cancellation(oneOffCancelledByThreshold).orElseThrow();
        assertEquals("THRESHOLD", row.cause());
        assertNull(row.reason());
    }

    @Test
    void aCancelledSeriesStaysCancelledAsASeries() {
        assertTrue(seriesFlag(seriesCancelled));
        assertTrue(cancellation(seriesCancelled).isEmpty());
    }

    /** Counted in whole days on the station's calendar: 00:30 on the 8th in Berlin is two days out. */
    @Test
    void aOneOffThresholdBecomesTheDaysBeforeItsDate() {
        assertEquals(2, thresholdDays(oneOffWithThreshold));
        assertEquals(0, thresholdDays(oneOffThresholdAfterStart));
    }

    /** A single deadline meant nothing sensible for a series, so it goes and the minimum stays. */
    @Test
    void aSeriesThresholdIsDroppedAndItsMinimumKept() {
        assertNull(thresholdDays(seriesWithThreshold));
        assertEquals(6, eventRepo.findById(seriesWithThreshold).orElseThrow().minRegistrations());
    }

    private static int oldEvent(
            String type, String start, boolean cancelled, String reason, String threshold, Integer minimum) {
        return query("""
                INSERT INTO station_event(station_id, name, event_type, day_of_week, start_time, end_time,
                                          cancelled, cancelled_at, cancel_reason, threshold_date, min_registrations)
                VALUES (:station_id, 'Alt', :type, 3, :start, :start, :cancelled, :cancelled_at, :reason,
                        :threshold, :minimum)
                RETURNING id;""")
                .single(call().bind("station_id", station.id())
                        .bind("type", type)
                        .bind("start", Instant.parse(start), INSTANT_TIMESTAMP)
                        .bind("cancelled", cancelled)
                        .bind(
                                "cancelled_at",
                                cancelled ? Instant.parse("2027-01-01T10:00:00Z") : null,
                                INSTANT_TIMESTAMP)
                        .bind("reason", reason)
                        .bind("threshold", threshold == null ? null : Instant.parse(threshold), INSTANT_TIMESTAMP)
                        .bind("minimum", minimum))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    private static Optional<Row> cancellation(int eventId) {
        return query("""
                SELECT event_date, cause, reason
                FROM event_date_cancellation
                WHERE event_id = :id;""")
                .single(call().bind("id", eventId))
                .map(row -> new Row(
                        row.getObject("event_date", LocalDate.class), row.getString("cause"), row.getString("reason")))
                .first();
    }

    private static boolean seriesFlag(int eventId) {
        return eventRepo.findById(eventId).orElseThrow().cancelled();
    }

    private static Integer thresholdDays(int eventId) {
        return eventRepo.findById(eventId).orElseThrow().thresholdDays();
    }

    private static String conversionPart() throws IOException {
        String patch;
        try (var in = Objects.requireNonNull(
                EventDateCancellationMigrationTest.class.getClassLoader().getResourceAsStream(PATCH))) {
            patch = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int from = patch.indexOf(FIRST_STATEMENT);
        int to = patch.indexOf(';', patch.indexOf(LAST_STATEMENT)) + 1;
        return patch.substring(from, to);
    }

    private static void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql.replace("ember_schema", schemaName));
        }
    }

    private record Row(LocalDate date, String cause, String reason) {}
}
