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
import java.util.List;
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
