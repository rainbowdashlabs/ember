/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every page a notification names exists in the app under that name and at that address.
 *
 * <p>A link carries a route name, which the in-app list navigates to as it is and a mail or a feed
 * turns into an address through {@link NotificationPages}. A name the app does not know breaks the
 * row in the list, and a name the table does not know sends a mail to the start page; both happened
 * for months without anybody noticing, so the three are read against each other here: the names the
 * backend writes, the table, and the page files of the app.
 */
class NotificationPagesTest {
    private static final Path PAGES = Path.of("frontend/src/pages");
    private static final Path SOURCES = Path.of("src/main/java");
    private static final Pattern LINK_ROUTE = Pattern.compile("NotificationLink\\(\\s*\"([a-z0-9-]+)\"");
    private static final Pattern ROUTE_CONSTANT =
            Pattern.compile("private static final String [A-Z_]+ = \"([a-z0-9-]+)\";");

    @Test
    void everyAddressInTheTableIsAPageOfThatName() {
        var wrong = new ArrayList<String>();
        NotificationPages.all().forEach((route, path) -> {
            var page = pageAt(path);
            if (page.isEmpty()) {
                wrong.add(route + ": no page at " + path);
            } else if (!read(page.get()).contains("name: '" + route + "'")) {
                wrong.add(route + ": the page at " + path + " goes by another name");
            }
        });
        assertEquals(List.of(), wrong);
    }

    @Test
    void everyRouteTheBackendWritesIsInTheTable() {
        var unknown = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            files.filter(file -> file.toString().endsWith(".java")).forEach(file -> {
                String source = read(file);
                LINK_ROUTE.matcher(source).results().forEach(match -> unknown.add(match.group(1)));
                if (file.endsWith("NotificationLinks.java")) {
                    ROUTE_CONSTANT.matcher(source).results().forEach(match -> unknown.add(match.group(1)));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        unknown.removeAll(NotificationPages.all().keySet());
        assertEquals(new TreeSet<String>(), unknown);
    }

    private static Optional<Path> pageAt(String path) {
        var dir = PAGES;
        var segments = path.substring(1).split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            var next = child(dir, segments[i], "");
            if (next.isEmpty()) return Optional.empty();
            dir = next.get();
        }
        var parent = dir;
        String last = segments[segments.length - 1];
        return child(parent, last, ".vue")
                .or(() -> child(parent, last, "").map(folder -> folder.resolve("index.vue")))
                .filter(Files::isRegularFile);
    }

    private static Optional<Path> child(Path dir, String segment, String suffix) {
        if (!segment.startsWith("{")) {
            var literal = dir.resolve(segment + suffix);
            return Files.exists(literal) ? Optional.of(literal) : Optional.empty();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(entry -> {
                        String name = entry.getFileName().toString();
                        return name.startsWith("[") && name.endsWith("]" + suffix);
                    })
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
