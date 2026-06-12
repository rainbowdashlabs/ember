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
        // member_group_entry has a FK to member_group, which has station_id
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
        // attendance_session_field -> attendance_session -> attendance_template -> station_id
        var path = resolver.resolve("attendance_session_field").orElseThrow();
        assertEquals("attendance_template", path.terminalTable());
        assertEquals(2, path.joins().size());
    }

    @Test
    void unknownTableYieldsEmpty() {
        assertTrue(resolver.resolve("not_a_real_table").isEmpty());
    }
}
