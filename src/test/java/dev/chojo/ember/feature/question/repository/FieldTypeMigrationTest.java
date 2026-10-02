/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question.repository;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The part of the migration that moves every custom field onto the shared type names, puts stored
 * answers into one shape per kind and pins the names with a check.
 *
 * <p>The schema of this class is built from the setup and every patch below the newest one, so the
 * old tables are the real ones. They are seeded with every old spelling in every table and with
 * answers in every shape found in use, and the newest patch then runs once, as it does on an
 * instance that updates.
 */
class FieldTypeMigrationTest extends RepositoryTestBase {
    private static final String DIRECTORY = "database/postgresql/1/";
    private static final int NEWEST_PATCH = 77;
    private static final List<String> TABLES = List.of(
            "profile_field",
            "cluster_profile_field",
            "inventory_field_definition",
            "board_field",
            "waiting_list_field",
            "attendance_template_field",
            "event_field",
            "event_template_field",
            "event_registration_field",
            "event_template_registration_field");

    private static String schema;
    private static Connection connection;
    private static Map<String, Long> definitionsBefore;
    private static int member;
    private static int profileBoolean;
    private static int profileText;
    private static int profileSection;
    private static int attendanceSession;
    private static int attendanceMember;
    private static int attendanceList;
    private static int eventBoolean;
    private static int eventList;
    private static int eventMember;
    private static int registrationMember;
    private static int defaultedEventField;
    private static int associationBirthDate;

    @BeforeAll
    static void migrate() throws IOException, SQLException {
        schema = "ember_field_types_" + System.nanoTime();
        connection = dataSource.getConnection();
        execute("CREATE SCHEMA " + schema + "; SET search_path TO " + schema + ";");
        execute(resource("setup.sql"));
        for (int patch = 1; patch < NEWEST_PATCH; patch++) {
            execute(resource("patch_" + patch + ".sql"));
        }
        seedTheOldTables();
        definitionsBefore = definitionCounts();
        execute(resource("patch_" + NEWEST_PATCH + ".sql"));
    }

    @AfterAll
    static void dropTheSchema() throws SQLException {
        execute("DROP SCHEMA " + schema + " CASCADE; SET search_path TO " + schemaName + ";");
        connection.close();
    }

    @Test
    void everyDefinitionIsKept() throws SQLException {
        assertEquals(definitionsBefore, definitionCounts());
    }

    @Test
    void noOldSpellingIsLeft() throws SQLException {
        for (String table : TABLES) {
            assertEquals(
                    0L,
                    single("SELECT count(*) FROM %s WHERE field_type IN ('STRING', 'TEXTAREA', 'ENUM', 'string')"
                            .formatted(table)),
                    table);
        }
    }

    @Test
    void eachOldSpellingBecomesItsSharedName() throws SQLException {
        assertEquals(
                Set.of("BOOLEAN", "CHOICE", "LONG_TEXT", "MEMBER", "MEMBER_LIST_OF_GROUP", "NUMBER", "TEXT"),
                set("SELECT field_type FROM event_field"));
        assertEquals(
                Set.of("BOOLEAN", "CHOICE", "LONG_TEXT", "MEMBER", "MEMBER_LIST_OF_GROUP", "MEMBER_OF_GROUP", "TEXT"),
                set("SELECT field_type FROM attendance_template_field"));
        assertEquals(Set.of("CHOICE", "LANE_ASSIGNEE", "TEXT"), set("SELECT field_type FROM board_field"));
        assertEquals(
                Set.of("AGE", "BOOLEAN", "CHOICE", "SECTION", "TEXT"), set("SELECT field_type FROM profile_field"));
        assertEquals(
                Set.of("BOOLEAN", "CHOICE", "LONG_TEXT", "MEMBER", "TEXT"),
                set("SELECT field_type FROM event_registration_field"));
        assertEquals(Set.of("TEXT", "MEMBER_OF_TAG"), set("SELECT field_type FROM event_template_field"));
        assertEquals(Set.of("CHOICE"), set("SELECT field_type FROM event_template_registration_field"));
        assertEquals(Set.of("TEXT", "CHOICE", "BOOLEAN"), set("SELECT field_type FROM waiting_list_field"));
        assertEquals(
                Set.of("CHOICE", "BOOLEAN", "SECTION", "DATE"), set("SELECT field_type FROM cluster_profile_field"));
    }

    /**
     * An association never declares a date of birth, each station does; one that slipped in anyway
     * becomes a plain date, keeping its answers, and none can be written from now on.
     */
    @Test
    void anAssociationDateOfBirthBecomesADate() throws SQLException {
        assertEquals("DATE", single("SELECT field_type FROM cluster_profile_field WHERE id = " + associationBirthDate));
        assertThrows(
                SQLException.class,
                () -> execute("UPDATE cluster_profile_field SET field_type = 'BIRTH_DATE' WHERE id = "
                        + associationBirthDate));
    }

    /** A field that relied on the old lower case default reads as a line of text like every other. */
    @Test
    void theLowerCaseDefaultIsReadAsText() throws SQLException {
        assertEquals("TEXT", single("SELECT field_type FROM event_field WHERE id = " + defaultedEventField));
        for (String table : List.of("board_field", "event_field", "event_template_field")) {
            assertEquals("'TEXT'::text", single("""
                            SELECT column_default FROM information_schema.columns
                            WHERE table_schema = '%s' AND table_name = '%s' AND column_name = 'field_type'""".formatted(schema, table)), table);
        }
    }

    /** A number is not renamed: whether it takes a fraction is its step, not its name. */
    @Test
    void numbersKeepTheirName() throws SQLException {
        assertEquals(1L, single("SELECT count(*) FROM event_field WHERE field_type = 'NUMBER'"));
        assertEquals(1L, single("SELECT count(*) FROM inventory_field_definition WHERE field_type = 'NUMBER'"));
    }

    @Test
    void anInventoryConfigNamesTheSharedKind() throws SQLException {
        assertEquals(
                Set.of("CHOICE:CHOICE", "NUMBER:NUMBER", "TEXT:TEXT"),
                set("SELECT field_type || ':' || (config ->> 'kind') FROM inventory_field_definition"));
        assertEquals("0.5", single("SELECT config ->> 'step' FROM inventory_field_definition WHERE key = 'laenge'"));
    }

    @Test
    void yesAndNoBecomeBooleans() throws SQLException {
        assertEquals(
                List.of("false", "true", "true"),
                strings("SELECT value::TEXT FROM profile_field_value WHERE field_id = %d ORDER BY 1"
                        .formatted(profileBoolean)));
        assertEquals("boolean", single("SELECT DISTINCT jsonb_typeof(value) FROM waiting_list_entry_value"));
        assertEquals("true", single("SELECT value FROM event_field WHERE id = " + eventBoolean));
        assertEquals(List.of("false", "true"), strings("SELECT value FROM event_field_date_value ORDER BY event_date"));
    }

    @Test
    void textIsLeftAsItWas() throws SQLException {
        assertEquals(
                "\"Müller\"", single("SELECT value::TEXT FROM profile_field_value WHERE field_id = " + profileText));
        assertEquals(
                Set.of("Mischkost", "true"),
                set("SELECT value FROM event_registration_field_value WHERE field_id <> " + registrationMember));
        assertEquals("Halle 3", single("SELECT value FROM event_field WHERE name LIKE 'STRING%'"));
    }

    @Test
    void answersUnderAHeadingAreRemoved() throws SQLException {
        assertEquals(0L, single("SELECT count(*) FROM profile_field_value WHERE field_id = " + profileSection));
        assertEquals(
                0L,
                single("SELECT count(*) FROM cluster_profile_field_value v JOIN cluster_profile_field f"
                        + " ON f.id = v.field_id WHERE f.field_type = 'SECTION'"));
    }

    @Test
    void membersOnASheetBecomeNumbers() throws SQLException {
        assertEquals(
                "6", single("SELECT value::TEXT FROM attendance_session_field WHERE field_id = " + attendanceMember));
        assertEquals(
                Set.of("[1, 2]", "[3]", "[4, 5]"),
                set("SELECT value::TEXT FROM attendance_session_field WHERE field_id = " + attendanceList));
    }

    /** An empty answer is no answer: the row goes rather than holding an empty string or list. */
    @Test
    void emptyMemberAnswersOnASheetAreRemoved() throws SQLException {
        assertEquals(0L, single("SELECT count(*) FROM attendance_session_field WHERE value IN ('\"\"', '[]')"));
        assertEquals(
                3L, single("SELECT count(*) FROM attendance_session_field WHERE session_id = " + attendanceSession));
        assertEquals("true", single("""
                SELECT v.value::TEXT FROM attendance_session_field v JOIN attendance_template_field f ON f.id = v.field_id
                WHERE f.field_type = 'BOOLEAN'"""));
    }

    @Test
    void membersOnAnAppointmentReadOneWay() throws SQLException {
        assertEquals("5", single("SELECT value FROM event_field WHERE id = " + eventMember));
        assertEquals(
                Set.of("", "[1,2]", "[1,6]"),
                set("SELECT value FROM event_field WHERE field_type = 'MEMBER_LIST_OF_GROUP'"));
        assertEquals(
                "7", single("SELECT value FROM event_registration_field_value WHERE field_id = " + registrationMember));
        assertEquals(1L, single("SELECT count(*) FROM event_field WHERE id = " + eventList + " AND value = ''"));
    }

    @Test
    void anOldSpellingIsRefusedFromNowOn() {
        for (String table : TABLES) {
            assertThrows(
                    SQLException.class,
                    () -> execute("UPDATE %s SET field_type = 'STRING' WHERE id IS NOT NULL".formatted(table)),
                    table);
        }
    }

    private static void seedTheOldTables() throws SQLException {
        int station = insert("INSERT INTO station (name) VALUES ('Alpha') RETURNING id;");
        int account = insert(
                "INSERT INTO account (email, first_name, last_name) VALUES ('types@test.com', 'Ty', 'Pen') RETURNING id;");
        member = insert("INSERT INTO station_member (station_id, account_id) VALUES (%d, %d) RETURNING id;"
                .formatted(station, account));

        seedProfile(station);
        seedCluster(station);
        seedInventory(station);
        seedBoard(station);
        seedWaitingList(station);
        seedAttendance(station);
        seedAppointment(station);
    }

    private static void seedProfile(int station) throws SQLException {
        profileText = field("profile_field", "station_id", station, "TEXT");
        int choice = field("profile_field", "station_id", station, "ENUM");
        profileBoolean = field("profile_field", "station_id", station, "BOOLEAN");
        profileSection = field("profile_field", "station_id", station, "SECTION");
        field("profile_field", "station_id", station, "AGE");
        int second = insert("INSERT INTO station_member (station_id) VALUES (%d) RETURNING id;".formatted(station));
        int third = insert("INSERT INTO station_member (station_id) VALUES (%d) RETURNING id;".formatted(station));
        int fourth = insert("INSERT INTO station_member (station_id) VALUES (%d) RETURNING id;".formatted(station));
        execute("""
                INSERT INTO profile_field_value (member_id, field_id, value) VALUES
                    (%1$d, %5$d, '"Müller"'),
                    (%1$d, %6$d, '"M"'),
                    (%1$d, %7$d, '"true"'),
                    (%2$d, %7$d, 'true'),
                    (%3$d, %7$d, '"0"'),
                    (%4$d, %7$d, '""'),
                    (%1$d, %8$d, '"Kopf"');""".formatted(member, second, third, fourth, profileText, choice, profileBoolean, profileSection));
    }

    private static void seedCluster(int station) throws SQLException {
        int cluster = insert(
                "INSERT INTO cluster (name, home_station_id) VALUES ('Verband', %d) RETURNING id;".formatted(station));
        field("cluster_profile_field", "cluster_id", cluster, "ENUM");
        int bool = field("cluster_profile_field", "cluster_id", cluster, "BOOLEAN");
        int section = field("cluster_profile_field", "cluster_id", cluster, "SECTION");
        associationBirthDate = field("cluster_profile_field", "cluster_id", cluster, "BIRTH_DATE");
        execute("""
                INSERT INTO cluster_profile_field_value (member_id, field_id, value) VALUES
                    (%1$d, %2$d, '"1"'),
                    (%1$d, %3$d, '"Kopf"');""".formatted(member, bool, section));
    }

    private static void seedInventory(int station) throws SQLException {
        int inventory = insert(
                "INSERT INTO inventory (station_id, name, inventory_type) VALUES (%d, 'Lager', 'INTERNAL') RETURNING id;"
                        .formatted(station));
        execute("""
                INSERT INTO inventory_field_definition (inventory_id, key, label, field_type, config) VALUES
                    (%1$d, 'groesse', 'Größe', 'ENUM', '{"kind": "ENUM", "options": []}'),
                    (%1$d, 'laenge', 'Länge', 'NUMBER', '{"kind": "NUMBER", "step": 0.5}'),
                    (%1$d, 'notiz', 'Notiz', 'TEXT', '{}');""".formatted(inventory));
    }

    private static void seedBoard(int station) throws SQLException {
        int board =
                insert("INSERT INTO board (station_id, name, short_key) VALUES (%d, 'Werkstatt', 'WS') RETURNING id;"
                        .formatted(station));
        field("board_field", "board_id", board, "STRING");
        field("board_field", "board_id", board, "ENUM");
        field("board_field", "board_id", board, "LANE_ASSIGNEE");
    }

    private static void seedWaitingList(int station) throws SQLException {
        int list = insert(
                "INSERT INTO waiting_list (station_id, name) VALUES (%d, 'Jugend') RETURNING id;".formatted(station));
        field("waiting_list_field", "list_id", list, "TEXT");
        field("waiting_list_field", "list_id", list, "ENUM");
        int bool = field("waiting_list_field", "list_id", list, "BOOLEAN");
        int entry = insert("""
                INSERT INTO waiting_list_entry (list_id, firstname, email, access_token)
                VALUES (%d, 'Kim', 'kim@test.com', 'token') RETURNING id;""".formatted(list));
        execute("INSERT INTO waiting_list_entry_value (entry_id, field_id, value) VALUES (%d, %d, '\"false\"');"
                .formatted(entry, bool));
    }

    private static void seedAttendance(int station) throws SQLException {
        int template = insert("INSERT INTO attendance_template (station_id, name) VALUES (%d, 'Dienst') RETURNING id;"
                .formatted(station));
        field("attendance_template_field", "template_id", template, "STRING");
        field("attendance_template_field", "template_id", template, "TEXTAREA");
        field("attendance_template_field", "template_id", template, "ENUM");
        field("attendance_template_field", "template_id", template, "MEMBER");
        int bool = field("attendance_template_field", "template_id", template, "BOOLEAN");
        attendanceMember = field("attendance_template_field", "template_id", template, "MEMBER_OF_GROUP");
        attendanceList = field("attendance_template_field", "template_id", template, "MEMBER_LIST_OF_GROUP");
        attendanceSession = insert("""
                INSERT INTO attendance_session (template_id, start_time, end_time)
                VALUES (%d, now(), now()) RETURNING id;""".formatted(template));
        int second = insert("""
                INSERT INTO attendance_session (template_id, start_time, end_time)
                VALUES (%d, now(), now()) RETURNING id;""".formatted(template));
        int third = insert("""
                INSERT INTO attendance_session (template_id, start_time, end_time)
                VALUES (%d, now(), now()) RETURNING id;""".formatted(template));
        int fourth = insert("""
                INSERT INTO attendance_session (template_id, start_time, end_time)
                VALUES (%d, now(), now()) RETURNING id;""".formatted(template));
        execute("""
                INSERT INTO attendance_session_field (session_id, field_id, value) VALUES
                    (%1$d, %5$d, '"1"'),
                    (%1$d, %6$d, '"6"'),
                    (%2$d, %6$d, '""'),
                    (%1$d, %7$d, '["1", "2"]'),
                    (%2$d, %7$d, '"[3]"'),
                    (%3$d, %7$d, '[4, 5]'),
                    (%4$d, %7$d, '[]');""".formatted(attendanceSession, second, third, fourth, bool, attendanceMember, attendanceList));
    }

    private static void seedAppointment(int station) throws SQLException {
        int event = insert("""
                INSERT INTO station_event (station_id, name, event_type, start_time, end_time)
                VALUES (%d, 'Übung', 'RECURRING', now(), now()) RETURNING id;""".formatted(station));
        eventField(event, "STRING", "Halle 3");
        eventField(event, "TEXTAREA", "lang");
        eventField(event, "ENUM", "M");
        eventField(event, "NUMBER", "4");
        eventBoolean = eventField(event, "BOOLEAN", "1");
        eventMember = eventField(event, "MEMBER", "\"5\"");
        eventList = eventField(event, "MEMBER_LIST_OF_GROUP", "[]");
        eventField(event, "MEMBER_LIST_OF_GROUP", "[1,6]");
        eventField(event, "MEMBER_LIST_OF_GROUP", "[\"1\", \"2\"]");
        defaultedEventField = insert(
                "INSERT INTO event_field (event_id, name) VALUES (%d, 'Ohne Typ') RETURNING id;".formatted(event));
        execute("""
                INSERT INTO event_field_date_value (field_id, event_date, value) VALUES
                    (%1$d, '2027-01-01', '0'),
                    (%1$d, '2027-01-08', 'TRUE');""".formatted(eventBoolean));

        int choice = field("event_registration_field", "event_id", event, "ENUM");
        int bool = field("event_registration_field", "event_id", event, "BOOLEAN");
        registrationMember = field("event_registration_field", "event_id", event, "MEMBER");
        field("event_registration_field", "event_id", event, "STRING");
        field("event_registration_field", "event_id", event, "TEXTAREA");
        int registration = insert("""
                INSERT INTO event_registration (event_id, member_id, event_date)
                VALUES (%d, %d, '2027-01-01') RETURNING id;""".formatted(event, member));
        execute("""
                INSERT INTO event_registration_field_value (registration_id, field_id, value) VALUES
                    (%1$d, %2$d, 'Mischkost'),
                    (%1$d, %3$d, '1'),
                    (%1$d, %4$d, '"7"');""".formatted(registration, choice, bool, registrationMember));

        int template = insert("INSERT INTO event_template (station_id, name) VALUES (%d, 'Vorlage') RETURNING id;"
                .formatted(station));
        field("event_template_field", "template_id", template, "STRING");
        field("event_template_field", "template_id", template, "MEMBER_OF_TAG");
        field("event_template_registration_field", "template_id", template, "ENUM");
    }

    private static int eventField(int event, String type, String value) throws SQLException {
        return insert("""
                INSERT INTO event_field (event_id, name, field_type, value)
                VALUES (%d, '%s', '%s', '%s') RETURNING id;""".formatted(event, type, type, value.replace("'", "''")));
    }

    private static int field(String table, String owner, int ownerId, String type) throws SQLException {
        return insert("INSERT INTO %s (%s, name, field_type) VALUES (%d, '%s', '%s') RETURNING id;"
                .formatted(table, owner, ownerId, type + " " + System.nanoTime(), type));
    }

    private static Map<String, Long> definitionCounts() throws SQLException {
        var counts = new TreeMap<String, Long>();
        for (String table : TABLES) {
            counts.put(table, (Long) single("SELECT count(*) FROM " + table));
        }
        return counts;
    }

    private static String resource(String file) throws IOException {
        try (var in = Objects.requireNonNull(
                FieldTypeMigrationTest.class.getClassLoader().getResourceAsStream(DIRECTORY + file))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("ember_schema", schema);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int insert(String sql) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static Set<String> set(String sql) throws SQLException {
        return new HashSet<>(strings(sql));
    }

    private static List<String> strings(String sql) throws SQLException {
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }

    private static Object single(String sql) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getObject(1);
        }
    }
}
