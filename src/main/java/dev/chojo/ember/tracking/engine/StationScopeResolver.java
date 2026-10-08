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
        for (var c : table.columns()) {
            if (STATION_ID_COLUMN.equals(c.name())) return true;
        }
        return false;
    }

    /**
     * Whether a foreign key is a soft reference that sets null on delete. Joining through one drops
     * rows whose value is null, and it expresses no ownership, so the scope chain skips it.
     */
    private static boolean isSoftReference(ForeignKey fk) {
        return "SET NULL".equalsIgnoreCase(fk.onDelete());
    }

    /**
     * Whether reaching this table identifies the owning station: it carries a station id, or it is the
     * station table itself, which cross-station tables reference directly from columns like
     * {@code owning_station_id}.
     */
    private boolean identifiesStation(String tableName) {
        if (STATION_TABLE.equals(tableName)) return true;
        var entry = tracking.tables().get(tableName);
        return entry != null && hasStationIdColumn(entry);
    }

    /** Walks the predecessors back from the terminal and returns the joins in root to terminal order. */
    private static ScopePath reconstructPath(String root, String terminal, Map<String, Step> predecessors) {
        List<Join> joins = new ArrayList<>();
        String node = terminal;
        while (!node.equals(root)) {
            Step step = predecessors.get(node);
            joins.add(new Join(step.parent(), step.fk()));
            node = step.parent();
        }
        Collections.reverse(joins);
        String scopeColumn = STATION_TABLE.equals(terminal) ? "id" : STATION_ID_COLUMN;
        return new ScopePath(terminal, scopeColumn, joins);
    }

    /**
     * Whether a column of the table can be empty. Joining through an empty column drops the row.
     */
    private static boolean isNullable(TableEntry table, String column) {
        for (var c : table.columns()) {
            if (column.equals(c.name())) return c.nullable();
        }
        return true;
    }

    /**
     * Returns the shortest path from {@code tableName} to a row identifying its owning station,
     * or empty when no such path exists.
     *
     * <p>A path through columns that cannot be empty is taken over a shorter one through a column that
     * can. Joining through an empty column drops the row, so a table scoped by such a column would lose
     * every row that leaves it empty, such as a restriction that names a user type instead of a group.
     */
    public Optional<ScopePath> resolve(String tableName) {
        return search(tableName, true).or(() -> search(tableName, false));
    }

    /**
     * The paths that together reach every row of {@code tableName} at its owning station. One path
     * through columns that cannot be empty reaches every row. A table whose every path runs through a
     * column that can be empty, such as a grant that names either a folder or a file, takes one path
     * per foreign key, and a row belongs to the station any of them reaches.
     *
     * @param tableName the table
     * @return the paths, empty when the table is unknown or reaches no station
     */
    public List<ScopePath> resolveAll(String tableName) {
        var strict = search(tableName, true);
        if (strict.isPresent()) return List.of(strict.get());
        var table = tracking.tables().get(tableName);
        if (table == null) return List.of();
        List<ScopePath> paths = new ArrayList<>();
        for (ForeignKey fk : table.foreignKeys()) {
            if (isSoftReference(fk) || tableName.equals(fk.refTable())) continue;
            var first = new Join(tableName, fk);
            resolve(fk.refTable()).ifPresent(rest -> {
                List<Join> joins = new ArrayList<>();
                joins.add(first);
                joins.addAll(rest.joins());
                paths.add(new ScopePath(rest.terminalTable(), rest.scopeColumn(), List.copyOf(joins)));
            });
        }
        return List.copyOf(paths);
    }

    /**
     * The shortest path from {@code tableName} to a row identifying its owning station.
     *
     * @param strict whether only columns that cannot be empty may be joined through
     */
    private Optional<ScopePath> search(String tableName, boolean strict) {
        var table = tracking.tables().get(tableName);
        if (table == null) return Optional.empty();

        if (STATION_TABLE.equals(tableName)) {
            return Optional.of(new ScopePath(tableName, "id", List.of()));
        }
        if (hasStationIdColumn(table)) {
            return Optional.of(new ScopePath(tableName, STATION_ID_COLUMN, List.of()));
        }

        Map<String, Step> predecessors = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(tableName);
        visited.add(tableName);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            var entry = tracking.tables().get(current);
            if (entry == null) continue;

            for (ForeignKey fk : entry.foreignKeys()) {
                String ref = fk.refTable();
                if (ref == null || visited.contains(ref)) continue;
                if (isSoftReference(fk)) continue;
                if (strict && isNullable(entry, fk.column())) continue;
                visited.add(ref);
                predecessors.put(ref, new Step(current, fk));

                if (identifiesStation(ref)) {
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
