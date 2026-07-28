/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level conventions for route classes that bytecode analysis cannot express:
 * station-ownership checks must go through the RouteSupport helpers (so the 403/404
 * policy stays centralized), and UUID path parameters must be read via pathUuid.
 */
class RouteSourceConventionsTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static final Pattern INLINE_STATION_COMPARISON = Pattern.compile(
            "\\.stationId\\(\\)\\s*!=\\s*session\\.stationId\\(\\)|session\\.stationId\\(\\)\\s*!=\\s*[\\w.]+\\.stationId\\(\\)");

    private static final Pattern INLINE_UUID_PATH_PARSE = Pattern.compile("UUID\\.fromString\\(\\s*ctx\\.pathParam");

    private static final Pattern PREFIX_ASSIGNMENT =
            Pattern.compile("String\\s+(\\w+)\\s*=\\s*prefix\\s*\\+\\s*\"([^\"]*)\"");

    private static final Pattern REGISTRATION =
            Pattern.compile("routes\\.(get|post|put|patch|delete)\\(\\s*(\\w+)\\s*\\+\\s*\"([^\"]*)\"");

    @Test
    void routesUseOwnershipHelpersInsteadOfInlineStationComparisons() throws IOException {
        assertNoMatches(
                INLINE_STATION_COMPARISON,
                "uses an inline station-id comparison; use RouteSupport.requireOwnedOrNotFound (default) or requireOwned");
    }

    @Test
    void routesUsePathUuidInsteadOfInlineParsing() throws IOException {
        assertNoMatches(INLINE_UUID_PATH_PARSE, "parses a UUID path parameter inline; use RouteSupport.pathUuid");
    }

    /**
     * Javalin answers with the first registered handler that matches, and the route multibinder is
     * consumed in binding order. {@code EventStructureRoutes} registers literal paths such as
     * {@code /events/categories} that {@code EventRoutes} would otherwise swallow with
     * {@code /events/{id}}, so its binding has to come first.
     */
    @Test
    void eventStructureRoutesAreBoundBeforeEventRoutes() throws IOException {
        List<String> lines =
                Files.readAllLines(Path.of("src", "main", "java", "dev", "chojo", "ember", "EmberModule.java"));
        int structure = bindingLine(lines, "EventStructureRoutes");
        int events = bindingLine(lines, "EventRoutes");
        assertTrue(
                structure < events,
                () -> "EventStructureRoutes must be bound before EventRoutes so its literal /events/* paths"
                        + " are matched before /events/{id}; found lines %d and %d".formatted(structure, events));
    }

    /**
     * {@code RemoteBoardWebhookRoutes} registers literal paths such as
     * {@code /remote/boards/webhook/mention} that the {@code /remote/boards/{boardKey}/...} routes of
     * the other remote board classes match just as well, so its binding has to come first.
     */
    @Test
    void remoteBoardWebhookRoutesAreBoundBeforeRemoteBoardRoutes() throws IOException {
        List<String> lines =
                Files.readAllLines(Path.of("src", "main", "java", "dev", "chojo", "ember", "EmberModule.java"));
        int webhooks = bindingLine(lines, "RemoteBoardWebhookRoutes");
        int boards = bindingLine(lines, "RemoteBoardRoutes");
        assertTrue(
                webhooks < boards,
                () -> "RemoteBoardWebhookRoutes must be bound before RemoteBoardRoutes so its literal"
                        + " /remote/boards/webhook/* paths are matched before /remote/boards/{boardKey}/*;"
                        + " found lines %d and %d".formatted(webhooks, boards));
    }

    /**
     * Javalin answers with the first registered handler that matches, so a literal path registered
     * after a parameter path that also matches it is dead — the parameter route wins and the literal
     * segment is parsed as the parameter value.
     *
     * <p>The two tests above pin specific orderings <em>between</em> route classes. This one is the
     * general rule <em>within</em> a class, which is where the failure actually showed up: splitting
     * the board routes uncovered {@code checklist/reorder} registered after {@code checklist/{itemId}}
     * and {@code tickets/reorder} after {@code tickets/{ticketNumber}}. Both were unreachable and
     * answered 400 on the integer parse, and neither was catchable by a between-class check.
     */
    @Test
    void noLiteralPathIsShadowedByAnEarlierParameterPath() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith("Routes.java"))
                    .toList()) {
                violations.addAll(shadowedRegistrations(path));
            }
        }
        assertTrue(
                violations.isEmpty(),
                () -> "Unreachable route registrations — a literal path is matched by an"
                        + " earlier parameter path in the same class, so the literal never runs:%n%s"
                                .formatted(String.join(System.lineSeparator(), violations)));
    }

    private List<String> shadowedRegistrations(Path path) throws IOException {
        List<Registration> registrations = registrationsIn(path);
        List<String> violations = new ArrayList<>();
        for (int later = 0; later < registrations.size(); later++) {
            for (int earlier = 0; earlier < later; earlier++) {
                Registration first = registrations.get(earlier);
                Registration second = registrations.get(later);
                if (first.shadows(second)) {
                    violations.add("%s:%d %s %s is unreachable — %s (line %d) matches it first"
                            .formatted(path, second.line, second.verb, second.path, first.path, first.line));
                }
            }
        }
        return violations;
    }

    /**
     * Reads the registrations of one route class in source order, resolving the local prefix
     * variables that most {@code register} methods build from the {@code prefix} argument.
     */
    private List<Registration> registrationsIn(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        Map<String, String> prefixes = new HashMap<>();
        prefixes.put("prefix", "");
        List<Registration> registrations = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher assignment = PREFIX_ASSIGNMENT.matcher(lines.get(i));
            if (assignment.find()) {
                prefixes.put(assignment.group(1), assignment.group(2));
            }
            Matcher registration = REGISTRATION.matcher(lines.get(i));
            if (registration.find()) {
                String base = prefixes.get(registration.group(2));
                if (base == null) continue;
                registrations.add(new Registration(registration.group(1), base + registration.group(3), i + 1));
            }
        }
        return registrations;
    }

    private record Registration(String verb, String path, int line) {

        private List<String> segments() {
            return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
        }

        private static boolean isParameter(String segment) {
            return segment.startsWith("{") || segment.startsWith("<");
        }

        /**
         * Whether this registration matches everything the other one does, while being less
         * specific — meaning the other one can never be reached.
         */
        boolean shadows(Registration other) {
            if (!verb.equals(other.verb) || path.equals(other.path)) return false;
            List<String> mine = segments();
            List<String> theirs = other.segments();
            if (mine.size() != theirs.size()) return false;
            boolean lessSpecific = false;
            for (int i = 0; i < mine.size(); i++) {
                String a = mine.get(i);
                String b = theirs.get(i);
                if (isParameter(a)) {
                    if (!isParameter(b)) lessSpecific = true;
                    continue;
                }
                if (!a.equals(b)) return false;
            }
            return lessSpecific;
        }
    }

    private int bindingLine(List<String> lines, String routeClass) {
        String binding = "addBinding().to(%s.class)".formatted(routeClass);
        return Stream.iterate(0, i -> i + 1)
                .limit(lines.size())
                .filter(i -> lines.get(i).contains(binding))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No route binding found for " + routeClass));
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
            throw new java.io.UncheckedIOException(e);
        }
    }
}
