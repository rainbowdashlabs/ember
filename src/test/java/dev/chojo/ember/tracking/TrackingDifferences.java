/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Names every entry in which a committed tracking file differs from what a refresh against the live
 * schema would write. The refresh timestamp is not compared, since every refresh sets a new one.
 */
final class TrackingDifferences {

    private TrackingDifferences() {}

    /**
     * Lists the differences between the committed tracking and the refreshed one, one line per entry,
     * empty when a refresh would change nothing but the timestamp.
     */
    static List<String> between(DataTracking committed, DataTracking refreshed) {
        List<String> differences = new ArrayList<>();
        if (committed.version() != refreshed.version()) {
            differences.add(changed("version", committed.version(), refreshed.version()));
        }
        if (!Objects.equals(committed.schemaHash(), refreshed.schemaHash())) {
            differences.add(changed("schemaHash", committed.schemaHash(), refreshed.schemaHash()));
        }
        var committedTables = orEmpty(committed.tables());
        var refreshedTables = orEmpty(refreshed.tables());
        for (var name : new TreeSet<>(union(committedTables.keySet(), refreshedTables.keySet()))) {
            differences.addAll(tableDifferences(name, committedTables.get(name), refreshedTables.get(name)));
        }
        if (!Objects.equals(committed.fileStores(), refreshed.fileStores())) {
            differences.add("fileStores: differ from a refresh");
        }
        return differences;
    }

    private static List<String> tableDifferences(String name, TableEntry committed, TableEntry refreshed) {
        if (committed == null) return List.of(name + ": missing from the committed file");
        if (refreshed == null) return List.of(name + ": no longer in the schema");
        if (committed.equals(refreshed)) return List.of();

        List<String> differences = new ArrayList<>();
        if (!Objects.equals(committed.description(), refreshed.description())) {
            differences.add(describedDifferently(name + " (table)", committed.description(), refreshed.description()));
        }
        if (!Objects.equals(committed.tableHash(), refreshed.tableHash())) {
            differences.add(changed(name + " tableHash", committed.tableHash(), refreshed.tableHash()));
        }
        if (!Objects.equals(committed.foreignKeys(), refreshed.foreignKeys())) {
            differences.add(name + ": foreign keys differ from the schema");
        }
        differences.addAll(columnDifferences(name, orEmpty(committed.columns()), orEmpty(refreshed.columns())));
        if (differences.isEmpty()) {
            differences.add(name + ": entry differs from a refresh");
        }
        return differences;
    }

    private static List<String> columnDifferences(
            String table, List<ColumnEntry> committed, List<ColumnEntry> refreshed) {
        var committedByName = byName(committed);
        var refreshedByName = byName(refreshed);
        List<String> differences = new ArrayList<>();
        for (var name : union(refreshedByName.keySet(), committedByName.keySet())) {
            String column = table + "." + name;
            var before = committedByName.get(name);
            var after = refreshedByName.get(name);
            if (before == null) {
                differences.add(column + ": missing from the committed file");
            } else if (after == null) {
                differences.add(column + ": no longer in the schema");
            } else if (!Objects.equals(before.description(), after.description())) {
                differences.add(describedDifferently(column, before.description(), after.description()));
            } else if (!before.equals(after)) {
                differences.add(changed(column, before, after));
            }
        }
        if (differences.isEmpty() && !committed.equals(refreshed)) {
            differences.add(table + ": column order differs from the schema");
        }
        return differences;
    }

    private static String describedDifferently(String entry, String committed, String comment) {
        return entry + ":\n      file says:    " + quoted(committed) + "\n      comment says: " + quoted(comment);
    }

    private static String changed(String entry, Object committed, Object refreshed) {
        return entry + ": file has " + committed + ", a refresh writes " + refreshed;
    }

    private static String quoted(String text) {
        return text == null ? "(none)" : "\"" + text + "\"";
    }

    private static Map<String, ColumnEntry> byName(List<ColumnEntry> columns) {
        return columns.stream()
                .collect(Collectors.toMap(ColumnEntry::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static <T> List<T> union(Collection<T> first, Collection<T> second) {
        var all = new LinkedHashSet<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }
}
