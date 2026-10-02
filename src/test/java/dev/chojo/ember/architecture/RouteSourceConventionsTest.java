/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route conventions that are about how an expression is written, and so can only be read from
 * source.
 *
 * <p>Everything a route registers, and everything a handler calls, is checked against the router and
 * the compiled classes in {@link RouteConventionsTest}. What is left here is the shape of a single
 * expression: which value is compared with which, which call a parsed value came from, and which
 * table a literal names. The compiled class keeps the calls and drops how their values meet, so a
 * comparison of two station ids and two unrelated reads of them look the same there.
 */
class RouteSourceConventionsTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static final Pattern INLINE_STATION_COMPARISON = Pattern.compile(
            "\\.stationId\\(\\)\\s*!=\\s*session\\.stationId\\(\\)|session\\.stationId\\(\\)\\s*!=\\s*[\\w.]+\\.stationId\\(\\)");

    private static final Pattern INLINE_UUID_PATH_PARSE = Pattern.compile("UUID\\.fromString\\(\\s*ctx\\.pathParam");

    private static final int HANDLER_CEILING = 40;

    private static final Pattern HANDLER = Pattern.compile(
            "\\s*(?:private|public|protected)?\\s*(?:static\\s+)?void\\s+(\\w+)\\(\\s*(?:final\\s+)?Context\\s+ctx\\s*\\)"
                    + "\\s*(?:throws [\\w, ]+)?\\s*\\{\\s*");

    private static final Pattern UNSCOPED_DELETE = Pattern.compile("deleteById\\(\\s*\"([a-z_]+)\"");

    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE TABLE [a-z_]+\\.([a-z_]+)\\s*\\(([^;]*?)\\n\\);", Pattern.DOTALL);

    /**
     * The unscoped deletes on station-carrying tables that existed when the rule was written. Every
     * one of them is reached through a handler that checks the station; the rule keeps the next one
     * from being added without that check. This list shrinks, never grows.
     */
    private static final Set<String> UNSCOPED_STATION_DELETES = Set.of(
            "attendance_report_preset",
            "attendance_template",
            "board",
            "checklist",
            "equipment_exchange_request",
            "equipment_procurement",
            "event_category",
            "event_template",
            "federation_partner",
            "form",
            "inventory",
            "inventory_container",
            "inventory_container_kind",
            "kb_file",
            "kb_folder",
            "kb_tag",
            "lost_and_found_item",
            "member_group",
            "news",
            "problem_report",
            "procedure",
            "profile_field",
            "quiz_catalog",
            "quiz_category",
            "quiz_test",
            "registration_code",
            "station_event",
            "station_event_break",
            "station_member",
            "station_page",
            "two_factor_policy",
            "user_tag",
            "waiting_list");

    /**
     * A station check is made by the ownership helpers, never by comparing two station ids in place,
     * so the answer to a row of another station is one {@code 404} decided in one place.
     */
    @Test
    void routesUseOwnershipHelpersInsteadOfInlineStationComparisons() throws IOException {
        assertNoMatches(
                INLINE_STATION_COMPARISON,
                "uses an inline station-id comparison; use RouteSupport.requireOwnedOrNotFound");
    }

    /** An identifier in the address is read by {@code pathUuid}, which answers a malformed one with a 404. */
    @Test
    void routesUsePathUuidInsteadOfInlineParsing() throws IOException {
        assertNoMatches(INLINE_UUID_PATH_PARSE, "parses a UUID path parameter inline; use RouteSupport.pathUuid");
    }

    /**
     * A table that carries a station is deleted from with the station in the statement.
     *
     * <p>{@code deleteById} takes a table and an id and asks nothing else, which makes the unscoped
     * delete the path of least resistance for a table that has a station to be scoped by, and that
     * has been the shape of every cross-station delete found here. {@code deleteByIdInStation}
     * beside it says the station, and a delete that names the wrong station removes nothing rather
     * than somebody else's row.
     *
     * <p>The list above is the state of the codebase when the rule was written, not a set of
     * blessed exceptions: each of those deletes is reached today through a handler the router rule
     * proves checks the station, so none of them is open, and each is one refactor away from the
     * scoped call. What the rule buys now is that the next one cannot be added. Shrink the list
     * when you touch the feature; never grow it.
     */
    @Test
    void noNewUnscopedDeleteOnAStationTable() throws IOException {
        Set<String> stationTables = tablesWithAStation();
        List<String> unscoped = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher deletes = UNSCOPED_DELETE.matcher(Files.readString(path));
                while (deletes.find()) {
                    String table = deletes.group(1);
                    if (stationTables.contains(table) && !UNSCOPED_STATION_DELETES.contains(table)) {
                        unscoped.add("%s: deleteById(\"%s\")".formatted(path.getFileName(), table));
                    }
                }
            }
        }
        assertTrue(unscoped.isEmpty(), () -> ("delete(s) by id on a table that carries a station; use"
                        + " SqlSupport.deleteByIdInStation so the statement names the station:%n%s")
                .formatted(String.join(System.lineSeparator(), unscoped)));
    }

    /**
     * A handler reads the request, hands it to a service and answers what came back, which fits in
     * {@value #HANDLER_CEILING} lines with room to spare. A handler that grows past that is doing
     * the service's work, where it can be neither reused nor tested without a server.
     */
    @Test
    void handlersStayShort() throws IOException {
        List<String> tooLong = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith("Routes.java"))
                    .toList()) {
                tooLong.addAll(longHandlersIn(path));
            }
        }
        assertTrue(tooLong.isEmpty(), () -> ("handler(s) longer than %d lines; move the work into a service:%n%s")
                .formatted(HANDLER_CEILING, String.join(System.lineSeparator(), tooLong)));
    }

    private static List<String> longHandlersIn(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        List<String> tooLong = new ArrayList<>();
        for (int start = 0; start < lines.size(); start++) {
            Matcher handler = HANDLER.matcher(lines.get(start));
            if (!handler.matches()) continue;
            int length = bodyLength(lines, start);
            if (length > HANDLER_CEILING) {
                tooLong.add("%s: %s (%d lines)".formatted(path.getFileName(), handler.group(1), length));
            }
        }
        return tooLong;
    }

    private static int bodyLength(List<String> lines, int start) {
        int depth = 0;
        for (int line = start; line < lines.size(); line++) {
            depth += braces(lines.get(line));
            if (depth == 0) return line - start + 1;
        }
        return lines.size() - start;
    }

    private static int braces(String line) {
        return (int) (line.chars().filter(c -> c == '{').count()
                - line.chars().filter(c -> c == '}').count());
    }

    /**
     * The tables whose {@code CREATE TABLE} declares a {@code station_id}, read from the migrations
     * rather than listed here, so a table that gains or loses its station is followed automatically.
     */
    private Set<String> tablesWithAStation() throws IOException {
        Set<String> tables = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "resources", "database"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                Matcher creates = CREATE_TABLE.matcher(Files.readString(path));
                while (creates.find()) {
                    if (creates.group(2).matches("(?s).*\\bstation_id\\b.*")) tables.add(creates.group(1));
                }
            }
        }
        return tables;
    }

    private void assertNoMatches(Pattern pattern, String message) throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            violations = files.filter(path -> path.getFileName().toString().endsWith("Routes.java"))
                    .flatMap(path -> matchesIn(path, pattern))
                    .toList();
        }
        assertTrue(violations.isEmpty(), () -> "%s:%n%s"
                .formatted(message, String.join(System.lineSeparator(), violations)));
    }

    private Stream<String> matchesIn(Path path, Pattern pattern) {
        try {
            List<String> lines = Files.readAllLines(path);
            return Stream.iterate(0, i -> i + 1)
                    .limit(lines.size())
                    .filter(i -> pattern.matcher(lines.get(i)).find())
                    .map(i -> "%s:%d %s".formatted(path, i + 1, lines.get(i).strip()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
