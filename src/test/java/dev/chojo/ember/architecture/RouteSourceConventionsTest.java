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
