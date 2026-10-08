/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.DataTrackingLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link StationScopeResolver} can derive a station-scope path
 * for every TRACKED table currently checked in to {@code data_tracking.json}.
 */
class StationScopeResolverTest {

    private static StationScopeResolver resolver;

    @BeforeAll
    static void setup() throws IOException {
        resolver = new StationScopeResolver(DataTrackingLoader.loadFromClasspath());
    }

    @Test
    void directlyScopedTableHasEmptyJoins() {
        var path = resolver.resolve("station_event").orElseThrow();
        assertEquals("station_event", path.terminalTable());
        assertEquals("station_id", path.scopeColumn());
        assertTrue(path.joins().isEmpty());
    }

    @Test
    void stationTableUsesIdAsScopeColumn() {
        var path = resolver.resolve("station").orElseThrow();
        assertEquals("station", path.terminalTable());
        assertEquals("id", path.scopeColumn());
        assertTrue(path.joins().isEmpty());
    }

    @Test
    void resolvesOneHopFkChain() {
        var path = resolver.resolve("member_group_entry").orElseThrow();
        assertEquals("member_group", path.terminalTable());
        assertEquals(1, path.joins().size());
        var hop = path.joins().getFirst();
        assertEquals("member_group_entry", hop.from());
        assertEquals("group_id", hop.fk().column());
        assertEquals("member_group", hop.fk().refTable());
        assertEquals("id", hop.fk().refColumn());
    }

    @Test
    void resolvesMultiHopFkChain() {
        var path = resolver.resolve("attendance_session_field").orElseThrow();
        assertEquals("attendance_template", path.terminalTable());
        assertEquals(2, path.joins().size());
    }

    @Test
    void unknownTableYieldsEmpty() {
        assertTrue(resolver.resolve("not_a_real_table").isEmpty());
        assertTrue(resolver.resolveAll("not_a_real_table").isEmpty());
    }

    /**
     * A restriction names a group or a user type. Scoped through the group, every restriction by user
     * type would be lost, so the path runs through the template, which every restriction names.
     */
    @Test
    void aColumnThatMayBeEmptyIsNotJoinedThroughWhileAnotherPathExists() {
        var path = resolver.resolve("event_template_restriction").orElseThrow();
        assertEquals("template_id", path.joins().getFirst().fk().column());

        var change = resolver.resolveAll("profile_field_change");
        assertEquals(1, change.size());
        assertEquals("member_id", change.getFirst().joins().getFirst().fk().column());
    }

    /**
     * A wiki grant names a folder or a file, either of which may be empty, so it takes one path per
     * foreign key and a grant belongs to the station any of them reaches.
     */
    @Test
    void aTableWhosePathsAllMayBeEmptyTakesOnePathPerForeignKey() {
        var paths = resolver.resolveAll("kb_access_grant");
        var firstHops = paths.stream()
                .map(path -> path.joins().getFirst().fk().column())
                .toList();
        assertTrue(firstHops.containsAll(List.of("folder_id", "file_id")), firstHops.toString());
    }
}
