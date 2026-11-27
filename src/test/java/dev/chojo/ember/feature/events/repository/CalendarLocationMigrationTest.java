/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one-off marking of the meeting point as the calendar's location, run over fields written the
 * way the earlier editor left them: the first place question named Treffpunkt or Meeting point of a
 * form is marked, unless another place of that form already is.
 */
class CalendarLocationMigrationTest extends RepositoryTestBase {
    private static final String PATCH = "database/postgresql/1/patch_93.sql";
    private static final String STATEMENTS = "-- A place question can be the location the calendar shows";

    private static Station station;
    private static int withBoth;
    private static int alreadyMarked;
    private static int inEnglish;
    private static int textNamedTreffpunkt;
    private static int template;
    private static int group;
    private static int groupAlreadyMarked;

    @BeforeAll
    static void writeFieldsAndMigrate() throws IOException, SQLException {
        station = stationRepo.create("Calendar Location " + UUID.randomUUID());
        withBoth = appointment();
        alreadyMarked = appointment();
        inEnglish = appointment();
        textNamedTreffpunkt = appointment();
        template = new EventTemplateRepository().create(station.id(), "Übung").id();
        group = eventGroup();
        groupAlreadyMarked = eventGroup();

        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(inSchema("""
                    INSERT INTO ember_schema.event_field(event_id, name, field_type, config, value, position) VALUES
                        (%1$d, 'Ort', 'LOCATION', '{}', 'Gerätehaus', 0),
                        (%1$d, 'Treffpunkt', 'LOCATION', '{}', 'Bahnhof', 1),
                        (%1$d, 'Treffpunkt', 'LOCATION', '{}', 'Markt', 2),
                        (%2$d, 'Ort', 'LOCATION', '{"calendarLocation": true}', 'Gerätehaus', 0),
                        (%2$d, 'Treffpunkt', 'LOCATION', '{}', 'Bahnhof', 1),
                        (%3$d, 'Meeting point', 'LOCATION', '{}', 'Station', 0),
                        (%4$d, 'Treffpunkt', 'TEXT', '{}', 'Bahnhof', 0)
                    """.formatted(withBoth, alreadyMarked, inEnglish, textNamedTreffpunkt)));
            statement.execute(inSchema("""
                    INSERT INTO ember_schema.event_template_field(template_id, name, field_type, config, position) VALUES
                        (%1$d, 'Ort', 'LOCATION', NULL, 0),
                        (%1$d, 'Treffpunkt', 'LOCATION', NULL, 1)
                    """.formatted(template)));
            statement.execute(inSchema("""
                    INSERT INTO ember_schema.event_group_field(event_group_id, name, field_type, config, position) VALUES
                        (%1$d, 'Ort', 'LOCATION', '{}', 0),
                        (%1$d, 'Treffpunkt', 'LOCATION', '{}', 1),
                        (%2$d, 'Sammelplatz', 'LOCATION', '{"calendarLocation": true}', 0),
                        (%2$d, 'Treffpunkt', 'LOCATION', '{}', 1)
                    """.formatted(group, groupAlreadyMarked)));
            statement.execute(inSchema(patch()));
        }
    }

    @AfterAll
    static void cleanupStation() {
        stationRepo.delete(station.id());
    }

    /** Of Ort and two meeting points, only the first meeting point becomes the calendar's location. */
    @Test
    void theFirstMeetingPointIsMarked() throws SQLException {
        assertEquals(List.of("Bahnhof"), marked("event_field", "event_id", withBoth));
    }

    /** A form whose calendar location was already chosen keeps that choice. */
    @Test
    void anEarlierChoiceStays() throws SQLException {
        assertEquals(List.of("Gerätehaus"), marked("event_field", "event_id", alreadyMarked));
    }

    /** The English name of the meeting point is marked too. */
    @Test
    void theEnglishNameIsMarked() throws SQLException {
        assertEquals(List.of("Station"), marked("event_field", "event_id", inEnglish));
    }

    /** A question named Treffpunkt that takes text and not a place is left alone. */
    @Test
    void onlyAPlaceIsMarked() throws SQLException {
        assertEquals(List.of(), marked("event_field", "event_id", textNamedTreffpunkt));
    }

    /** A template's meeting point is marked, settings of none at all included. */
    @Test
    void aTemplatesMeetingPointIsMarked() throws SQLException {
        assertEquals(List.of("Treffpunkt"), marked("event_template_field", "template_id", template));
    }

    /** An event group's meeting point is marked, and one whose choice was made keeps it. */
    @Test
    void anEventGroupsMeetingPointIsMarked() throws SQLException {
        assertEquals(List.of("Treffpunkt"), marked("event_group_field", "event_group_id", group));
        assertEquals(List.of("Sammelplatz"), marked("event_group_field", "event_group_id", groupAlreadyMarked));
    }

    private static List<String> marked(String table, String owner, int id) throws SQLException {
        var column = table.equals("event_field") ? "value" : "name";
        var marked = new ArrayList<String>();
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(inSchema(
                        "SELECT %s FROM ember_schema.%s WHERE %s = ? AND (config ->> 'calendarLocation')::BOOLEAN IS TRUE"
                                .formatted(column, table, owner)))) {
            statement.setInt(1, id);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) marked.add(rows.getString(1));
            }
        }
        return marked;
    }

    private static String inSchema(String sql) {
        return sql.replace("ember_schema", schemaName);
    }

    /** The marking of the meeting points alone, without what the same release patch does before it. */
    private static String patch() throws IOException {
        try (var in = Objects.requireNonNull(
                CalendarLocationMigrationTest.class.getClassLoader().getResourceAsStream(PATCH))) {
            var patch = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return patch.substring(patch.indexOf(STATEMENTS));
        }
    }

    private static int eventGroup() {
        return new EventGroupRepository()
                .create(
                        station.id(),
                        new EventGroupDetails("Jugendflamme", null, null, RegistrationDeadline.none(), false, null))
                .id();
    }

    private static int appointment() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        return eventRepo
                .create(
                        station.id(),
                        "Übung",
                        "desc",
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        null,
                        true,
                        RegistrationDeadline.none(),
                        false,
                        null,
                        null,
                        null,
                        null)
                .id();
    }
}
