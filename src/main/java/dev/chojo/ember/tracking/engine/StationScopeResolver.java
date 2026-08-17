/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.TableEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the chain of JOINs required to scope an arbitrary table's rows to a
 * single station by walking the foreign-key graph in {@link DataTracking}.
 *
 * <p>The result is a {@link ScopePath} describing either:
 * <ul>
 *   <li>a direct scope (the table itself has a {@code station_id} column), or</li>
 *   <li>a chain of FK joins that eventually lead to a table carrying {@code station_id}.</li>
 * </ul>
 */
public final class StationScopeResolver {

    /**
     * Column name used by all station-scoped tables to point at their owning station.
     */
    public static final String STATION_ID_COLUMN = "station_id";

    /**
     * The table that represents the station itself; its {@code id} column carries the station id.
     */
    public static final String STATION_TABLE = "station";

    private final DataTracking tracking;

    public StationScopeResolver(DataTracking tracking) {
        this.tracking = tracking;
    }

    private static boolean hasStationIdColumn(TableEntry table) {
        if (table.columns() == null) return false;
        for (var c : table.columns()) {
            if (STATION_ID_COLUMN.equals(c.name())) return true;
        }
        return false;
    }

    private static ScopePath reconstructPath(String root, String terminal, Map<String, Step> predecessors) {
        List<Join> joins = new ArrayList<>();
        String node = terminal;
        while (!node.equals(root)) {
            Step step = predecessors.get(node);
            joins.add(new Join(step.parent(), step.fk()));
            node = step.parent();
        }
        // The BFS reconstructs from terminal back to root; reverse to get root → terminal order.
        Collections.reverse(joins);
        String scopeColumn = STATION_TABLE.equals(terminal) ? "id" : STATION_ID_COLUMN;
        return new ScopePath(terminal, scopeColumn, joins);
    }

    /**
     * Returns the shortest path from {@code tableName} to a row identifying its owning station,
     * or empty when no such path exists.
     */
    public Optional<ScopePath> resolve(String tableName) {
        var table = tracking.tables() == null ? null : tracking.tables().get(tableName);
        if (table == null) return Optional.empty();

        if (STATION_TABLE.equals(tableName)) {
            return Optional.of(new ScopePath(tableName, "id", List.of()));
        }
        if (hasStationIdColumn(table)) {
            return Optional.of(new ScopePath(tableName, STATION_ID_COLUMN, List.of()));
        }

        // BFS over FK edges. predecessors[node] = the FK edge that led to it (and the parent node).
        Map<String, Step> predecessors = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(tableName);
        visited.add(tableName);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            var entry = tracking.tables().get(current);
            if (entry == null || entry.foreignKeys() == null) continue;

            for (ForeignKey fk : entry.foreignKeys()) {
                String ref = fk.refTable();
                if (ref == null || visited.contains(ref)) continue;
                // SET NULL FKs are soft references - joining through them drops rows whose value is
                // null. They don't express an ownership relationship, so the scope chain skips them.
                if ("SET NULL".equalsIgnoreCase(fk.onDelete())) continue;
                visited.add(ref);
                predecessors.put(ref, new Step(current, fk));

                var refEntry = tracking.tables().get(ref);
                // The station table is itself a terminal - the FK that landed us here already
                // identifies the owning station. Required for cross-station tables that carry
                // FKs like {owning,requesting}_station_id straight to station(id) instead of
                // routing through a station_id-bearing intermediate (e.g. federation_lending_*).
                if (STATION_TABLE.equals(ref) || (refEntry != null && hasStationIdColumn(refEntry))) {
                    return Optional.of(reconstructPath(tableName, ref, predecessors));
                }
                queue.add(ref);
            }
        }

        return Optional.empty();
    }

    private record Step(String parent, ForeignKey fk) {}

    /**
     * A single FK join in a scope path. {@code from} is the parent (already in the path),
     * {@code fk} is the foreign key that points from {@code from} to {@link ForeignKey#refTable()}.
     */
    public record Join(String from, ForeignKey fk) {}

    /**
     * The terminal table carrying the station-id signal and the chain of FK joins to reach it.
     *
     * @param terminalTable the table where the {@code scopeColumn} lives
     * @param scopeColumn   the column on {@code terminalTable} to filter by (typically {@code station_id},
     *                      or {@code id} when {@code terminalTable} is the {@code station} table itself)
     * @param joins         FK joins from the source table to {@code terminalTable}; empty when the source
     *                      table already carries the scope column
     */
    public record ScopePath(String terminalTable, String scopeColumn, List<Join> joins) {}
}
