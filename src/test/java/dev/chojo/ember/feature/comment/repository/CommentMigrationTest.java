/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.repository;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The move of the four comment tables into one, run on a database built up to the version before
 * it and filled the way the old code filled it.
 *
 * <p>The schema of this class is built from the setup and every patch below the newest one, so the
 * old tables are the real ones and not a copy of them. The newest patch then runs once, as it does
 * on an instance that updates.
 */
class CommentMigrationTest extends RepositoryTestBase {
    private static final String DIRECTORY = "database/postgresql/1/";
    private static final int NEWEST_PATCH = 77;

    private static String schema;
    private static Connection connection;
    private static int stationA;
    private static int stationB;
    private static int member;
    private static int systemNews;

    @BeforeAll
    static void migrate() throws IOException, SQLException {
        schema = "ember_migration_" + System.nanoTime();
        connection = dataSource.getConnection();
        execute("CREATE SCHEMA " + schema + "; SET search_path TO " + schema + ";");
        execute(resource("setup.sql"));
        for (int patch = 1; patch < NEWEST_PATCH; patch++) {
            execute(resource("patch_" + patch + ".sql"));
        }
        seedTheOldTables();
        execute(resource("patch_" + NEWEST_PATCH + ".sql"));
    }

    @AfterAll
    static void dropTheSchema() throws SQLException {
        execute("DROP SCHEMA " + schema + " CASCADE; SET search_path TO " + schemaName + ";");
        connection.close();
    }

    private static String resource(String file) throws IOException {
        try (var in = Objects.requireNonNull(
                CommentMigrationTest.class.getClassLoader().getResourceAsStream(DIRECTORY + file))) {
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

    private static List<List<Object>> rows(String sql) throws SQLException {
        var result = new ArrayList<List<Object>>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                var row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    row.add(rows.getObject(column));
                }
                result.add(row);
            }
        }
        return result;
    }

    private static Object single(String sql) throws SQLException {
        return rows(sql).getFirst().getFirst();
    }

    private static void seedTheOldTables() throws SQLException {
        stationA = insert("INSERT INTO station (name) VALUES ('Alpha') RETURNING id;");
        stationB = insert("INSERT INTO station (name) VALUES ('Beta') RETURNING id;");
        int account = insert(
                "INSERT INTO account (email, first_name, last_name) VALUES ('migration@test.com', 'Mia', 'Gration') RETURNING id;");
        member = insert("INSERT INTO station_member (station_id, account_id) VALUES (%d, %d) RETURNING id;"
                .formatted(stationA, account));

        int event = insert("""
                INSERT INTO station_event (station_id, name, event_type, start_time, end_time)
                VALUES (%d, 'Sommerfest', 'RECURRING', now(), now()) RETURNING id;""".formatted(stationA));
        execute("""
                INSERT INTO event_comment (id, event_id, content, created_at)
                VALUES (100, %1$d, 'Termin oben', '2027-01-01T10:00:00Z');
                INSERT INTO event_comment (id, event_id, parent_id, content, event_date, created_at)
                VALUES (101, %1$d, 100, 'Termin Antwort', '2027-05-01', '2027-01-01T11:00:00Z');
                INSERT INTO event_comment (id, event_id, content, deleted, created_at)
                VALUES (102, %1$d, '', TRUE, '2027-01-01T12:00:00Z');
                INSERT INTO event_comment (id, event_id, parent_id, content, updated_at, created_at)
                VALUES (103, %1$d, 102, 'unter Geloeschtem', '2027-01-02T10:00:00Z', '2027-01-01T13:00:00Z');
                """.formatted(event));

        int news = insert(
                "INSERT INTO news (station_id, title, content_markdown, content_html) VALUES (%d, 'Neu', 'x', 'x') RETURNING id;"
                        .formatted(stationB));
        systemNews = insert(
                "INSERT INTO news (station_id, title, content_markdown, content_html) VALUES (NULL, 'An alle', 'x', 'x') RETURNING id;");
        int newsTop = insert(
                "INSERT INTO news_comment (news_id, content, created_at) VALUES (%d, 'News oben', '2027-02-01 10:00:00') RETURNING id;"
                        .formatted(news));
        insert("INSERT INTO news_comment (news_id, parent_id, content) VALUES (%d, %d, 'News Antwort') RETURNING id;"
                .formatted(news, newsTop));
        insert("INSERT INTO news_comment (news_id, content) VALUES (%d, 'Systemkommentar') RETURNING id;"
                .formatted(systemNews));

        int file = insert("""
                INSERT INTO kb_file (station_id, name, file_type, created_by)
                VALUES (%d, 'Handbuch', 'MARKDOWN', %d) RETURNING id;""".formatted(stationA, member));
        int kbTop = insert(
                "INSERT INTO kb_comment (file_id, content) VALUES (%d, 'Datei oben') RETURNING id;".formatted(file));
        insert("INSERT INTO kb_comment (file_id, parent_id, content) VALUES (%d, %d, 'Datei Antwort') RETURNING id;"
                .formatted(file, kbTop));

        int board = insert("INSERT INTO board (station_id, name, short_key) VALUES (%d, 'Brett', 'MIG') RETURNING id;"
                .formatted(stationB));
        int lane = insert("INSERT INTO board_lane (board_id, name, position) VALUES (%d, 'Offen', 0) RETURNING id;"
                .formatted(board));
        int ticket = insert("""
                INSERT INTO board_ticket (board_id, lane_id, ticket_number, title, position,
                                          creator_station_uid, creator_member_uid)
                VALUES (%d, %d, 1, 'Aufgabe', 0, gen_random_uuid(), gen_random_uuid()) RETURNING id;""".formatted(board, lane));
        int ticketTop =
                insert("INSERT INTO board_ticket_comment (ticket_id, content) VALUES (%d, 'Ticket oben') RETURNING id;"
                        .formatted(ticket));
        insert(
                "INSERT INTO board_ticket_comment (ticket_id, parent_id, content) VALUES (%d, %d, 'Ticket Antwort') RETURNING id;"
                        .formatted(ticket, ticketTop));

        notification("event-detail", "{\"id\": %d}".formatted(event), 101, true);
        notification("news-detail", "{\"id\": %d}".formatted(news), 2, true);
        notification("kb-file", "{\"id\": %d}".formatted(file), 2, false);
        notification(
                "ticket-detail",
                "{\"boardKey\": \"MIG\", \"ticketNumber\": 1, \"ticketId\": %d}".formatted(ticket),
                2,
                true);
        execute("""
                INSERT INTO notification (member_id, type, data)
                VALUES (%d, 'NEWS_CREATED', '{"link": {"route": "news-detail", "routeParams": {"id": 1}}}');""".formatted(member));
    }

    private static void notification(String route, String params, int comment, boolean unread) throws SQLException {
        String data = "{\"link\": {\"route\": \"%s\", \"routeParams\": %s, \"query\": {\"comment\": %d}}}"
                .formatted(route, params, comment);
        execute("""
                INSERT INTO notification (member_id, type, data, acknowledged_at)
                VALUES (%1$d, 'NEWS_COMMENT', '%2$s', %3$s);""".formatted(member, data, unread ? "NULL" : "now()"));
    }

    private static List<Object> comment(String content) throws SQLException {
        return rows("""
                SELECT id, station_id, event_id, event_date, news_id, kb_file_id, board_ticket_id, parent_id, deleted
                FROM comment WHERE content = '%s';""".formatted(content)).getFirst();
    }

    private static int id(String content) throws SQLException {
        return (Integer) comment(content).getFirst();
    }

    @Test
    void everyCommentArrivesOnce() throws SQLException {
        assertEquals(11L, single("SELECT count(*) FROM comment;"));
        assertEquals(4L, single("SELECT count(*) FROM comment WHERE event_id IS NOT NULL;"));
        assertEquals(3L, single("SELECT count(*) FROM comment WHERE news_id IS NOT NULL;"));
        assertEquals(2L, single("SELECT count(*) FROM comment WHERE kb_file_id IS NOT NULL;"));
        assertEquals(2L, single("SELECT count(*) FROM comment WHERE board_ticket_id IS NOT NULL;"));
    }

    @Test
    void appointmentCommentsKeepTheirIdsDatesAndThreads() throws SQLException {
        var reply = comment("Termin Antwort");
        assertEquals(101, reply.getFirst());
        assertEquals(stationA, reply.get(1));
        assertEquals(java.sql.Date.valueOf("2027-05-01"), reply.get(3));
        assertEquals(100, reply.get(7));
        assertEquals(102, comment("unter Geloeschtem").get(7));
        assertTrue((Boolean) comment("").get(8));
    }

    @Test
    void theOtherKindsAreRenumberedAboveTheAppointmentsWithParentsFirst() throws SQLException {
        int newsTop = id("News oben");
        int newsReply = id("News Antwort");
        int kbTop = id("Datei oben");
        int kbReply = id("Datei Antwort");
        int ticketTop = id("Ticket oben");
        int ticketReply = id("Ticket Antwort");

        assertTrue(newsTop > 103);
        assertTrue(newsTop < newsReply);
        assertTrue(kbTop < kbReply);
        assertTrue(ticketTop < ticketReply);
        assertEquals(newsTop, comment("News Antwort").get(7));
        assertEquals(kbTop, comment("Datei Antwort").get(7));
        assertEquals(ticketTop, comment("Ticket Antwort").get(7));
        assertNull(comment("News oben").get(7));
    }

    @Test
    void everyCommentCarriesTheStationOfItsTarget() throws SQLException {
        assertEquals(stationB, comment("News oben").get(1));
        assertEquals(stationA, comment("Datei oben").get(1));
        assertEquals(stationB, comment("Ticket oben").get(1));
        var underSystemEntry = comment("Systemkommentar");
        assertNull(underSystemEntry.get(1));
        assertEquals(systemNews, underSystemEntry.get(4));
    }

    @Test
    void notificationLinksFollowTheirComment() throws SQLException {
        assertEquals("101", link("event-detail"));
        assertEquals(String.valueOf(id("News Antwort")), link("news-detail"));
        assertEquals(String.valueOf(id("Datei Antwort")), link("kb-file"));
        assertEquals(String.valueOf(id("Ticket Antwort")), link("ticket-detail"));
        assertEquals(1L, single("SELECT count(*) FROM notification WHERE data -> 'link' -> 'query' IS NULL;"));
    }

    @Test
    void theDeduplicationKeyFollowsTheRewrittenLink() throws SQLException {
        assertEquals(0L, single("""
                        SELECT count(*) FROM notification
                        WHERE dedup_key IS NOT NULL AND dedup_key <> md5(type || data::TEXT);"""));
        assertEquals(1L, single("""
                        SELECT count(*) FROM notification
                        WHERE dedup_key IS NULL AND data -> 'link' ->> 'route' = 'kb-file';"""));
    }

    @Test
    void aNewCommentTakesTheNextFreeId() throws SQLException {
        int highest = (Integer) single("SELECT max(id) FROM comment;");
        int next = insert("""
                INSERT INTO comment (station_id, kb_file_id, content)
                SELECT station_id, kb_file_id, 'neu' FROM comment WHERE content = 'Datei oben'
                RETURNING id;""");
        execute("DELETE FROM comment WHERE id = %d;".formatted(next));

        assertEquals(highest + 1, next);
    }

    @Test
    void theOldTablesAreGone() throws SQLException {
        assertFalse(rows("""
                        SELECT 1 FROM information_schema.tables
                        WHERE table_schema = '%s'
                          AND table_name IN ('event_comment', 'news_comment', 'kb_comment', 'board_ticket_comment');""".formatted(schema)).iterator().hasNext());
    }

    private static String link(String route) throws SQLException {
        return (String) single(
                "SELECT data -> 'link' -> 'query' ->> 'comment' FROM notification WHERE data -> 'link' ->> 'route' = '%s' AND data -> 'link' -> 'query' IS NOT NULL;"
                        .formatted(route));
    }
}
