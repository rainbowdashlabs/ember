/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.ArrayReference;
import dev.chojo.ember.tracking.CustomScope;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.TrackingStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Derives the export/import order for TRACKED tables from {@link DataTracking}.
 *
 * <p>Tables are sorted topologically by their foreign-key dependencies: a table that
 * references another must be exported/imported after the referenced one. Self-FKs and
 * cross-references back to higher-up tables are ignored (they break ties on the second
 * pass - final ordering is otherwise stable alphabetical within a layer).
 *
 * <p>A {@link CustomScope} is not an ordering dependency. It is an export-side concept that
 * filters which rows belong to a station by a detour through another table; the foreign keys
 * still decide the insert order. Treating it as a dependency would create false cycles such as
 * {@code account} and {@code station_member} pointing at each other.
 */
public final class TableOrder {

    private TableOrder() {}

    /**
     * Returns the topologically-sorted list of TRACKED tables in {@code tracking}.
     *
     * <p>Every foreign key between tracked tables takes part in the graph, so the importer has
     * the remapped ids of a referenced table in hand before it reaches the referencing one.
     * Cycles are broken by dropping a {@code SET NULL} edge that lies on one. A cycle of keys that
     * cannot be dropped, such as a borrowed item naming its lending line while a line names the
     * item it asks for, leaves its tables and everything after them appended alphabetically so
     * the output stays stable; the importer holds back each row until the row it names has
     * arrived, see {@link WaitingRows}.
     */
    public static List<String> topological(DataTracking tracking) {
        Set<String> tracked = trackedTables(tracking);
        Map<String, Set<String>> dependsOn = new TreeMap<>();
        Map<String, Set<String>> softEdges = new TreeMap<>();
        for (String name : tracked) {
            dependsOn.put(name, new HashSet<>());
            softEdges.put(name, new HashSet<>());
        }
        for (String name : tracked) {
            addForeignKeyEdges(tracking, name, tracked, dependsOn, softEdges);
        }
        dropCyclicSoftEdges(dependsOn, softEdges);

        List<String> ordered = kahnSort(dependsOn);
        if (ordered.size() < tracked.size()) {
            var combined = new ArrayList<>(ordered);
            for (String name : new TreeSet<>(tracked)) {
                if (!combined.contains(name)) combined.add(name);
            }
            return List.copyOf(combined);
        }
        return ordered;
    }

    /** The names of the tables whose station transfer is TRACKED. */
    private static Set<String> trackedTables(DataTracking tracking) {
        Set<String> tracked = new HashSet<>();
        for (var e : tracking.tables().entrySet()) {
            var t = e.getValue();
            if (t.stationTransfer() != null && t.stationTransfer().status() == TrackingStatus.TRACKED) {
                tracked.add(e.getKey());
            }
        }
        return tracked;
    }

    /**
     * Records that {@code name} depends on every other tracked table one of its foreign keys
     * points at. A self-reference is no dependency. A {@code SET NULL} key is also noted in
     * {@code softEdges}, because that edge is the one that may be dropped to break a cycle.
     *
     * <p>An {@link ArrayReference} is a dependency like a foreign key, since its elements are
     * remapped the same way. The database lets a referenced row go without touching the array, so
     * it is soft as well.
     */
    private static void addForeignKeyEdges(
            DataTracking tracking,
            String name,
            Set<String> tracked,
            Map<String, Set<String>> dependsOn,
            Map<String, Set<String>> softEdges) {
        var table = tracking.tables().get(name);
        for (ArrayReference reference :
                Objects.requireNonNullElse(table.arrayReferences(), List.<ArrayReference>of())) {
            String ref = reference.refTable();
            if (ref.equals(name) || !tracked.contains(ref)) continue;
            dependsOn.get(name).add(ref);
            softEdges.get(name).add(ref);
        }
        for (ForeignKey fk : table.foreignKeys()) {
            String ref = fk.refTable();
            if (ref == null || ref.equals(name) || !tracked.contains(ref)) continue;
            dependsOn.get(name).add(ref);
            if ("SET NULL".equalsIgnoreCase(fk.onDelete())) {
                softEdges.get(name).add(ref);
            }
        }
    }

    /**
     * Drops every soft edge {@code A -> B} for which a path {@code B => A} exists in the full graph.
     * Such a path proves the edge lies on a cycle, and a {@code SET NULL} key is the cheapest place
     * to break it. Soft edges that are not on a cycle stay, so the natural order is kept (for
     * example an item's size keeps the size table sorted before the items).
     */
    private static void dropCyclicSoftEdges(Map<String, Set<String>> dependsOn, Map<String, Set<String>> softEdges) {
        for (var entry : new TreeMap<>(softEdges).entrySet()) {
            String from = entry.getKey();
            for (String to : new TreeSet<>(entry.getValue())) {
                if (canReach(to, from, dependsOn)) {
                    dependsOn.get(from).remove(to);
                }
            }
        }
    }

    /**
     * Whether {@code start} can reach {@code goal} by following the directed edges in
     * {@code graph}. Used to detect SCCs containing a specific edge: if {@code start} can reach
     * the predecessor of an edge that points back to it, the edge participates in a cycle.
     */
    private static boolean canReach(String start, String goal, Map<String, Set<String>> graph) {
        if (start.equals(goal)) return true;
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            String node = stack.pop();
            if (!visited.add(node)) continue;
            for (String next : graph.getOrDefault(node, Set.of())) {
                if (next.equals(goal)) return true;
                if (!visited.contains(next)) stack.push(next);
            }
        }
        return false;
    }

    /**
     * Kahn's algorithm over {@code dependsOn}, taking the ready tables of each layer in
     * alphabetical order so the output is stable.
     *
     * <p>Tables that are still waiting on a dependency at the end are part of a foreign-key cycle.
     * They are appended alphabetically so they still appear, and the caller must be prepared to
     * set their cyclic keys later.
     */
    private static List<String> kahnSort(Map<String, Set<String>> dependsOn) {
        Map<String, Set<String>> dependents = dependentsOf(dependsOn);
        Map<String, Integer> remaining = new TreeMap<>();
        for (var e : dependsOn.entrySet()) {
            remaining.put(e.getKey(), e.getValue().size());
        }

        List<String> result = new ArrayList<>(dependsOn.size());
        var ready = new TreeSet<String>();
        for (var e : remaining.entrySet()) {
            if (e.getValue() == 0) ready.add(e.getKey());
        }

        while (!ready.isEmpty()) {
            String next = ready.pollFirst();
            result.add(next);
            for (String dependent : dependents.getOrDefault(next, Set.of())) {
                int left = remaining.merge(dependent, -1, Integer::sum);
                if (left == 0) ready.add(dependent);
            }
        }

        for (var e : remaining.entrySet()) {
            if (e.getValue() > 0 && !result.contains(e.getKey())) result.add(e.getKey());
        }
        return List.copyOf(result);
    }

    /** The reverse of {@code dependsOn}: for every table, the tables that depend on it. */
    private static Map<String, Set<String>> dependentsOf(Map<String, Set<String>> dependsOn) {
        Map<String, Set<String>> dependents = new TreeMap<>();
        for (String n : dependsOn.keySet()) dependents.put(n, new HashSet<>());
        for (var e : dependsOn.entrySet()) {
            for (String dep : e.getValue()) {
                dependents.computeIfAbsent(dep, k -> new HashSet<>()).add(e.getKey());
            }
        }
        return dependents;
    }
}
