/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.CustomScope;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.Status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Derives the export/import order for TRACKED tables from {@link DataTracking}.
 *
 * <p>Tables are sorted topologically by their foreign-key dependencies: a table that
 * references another must be exported/imported after the referenced one. Self-FKs and
 * cross-references back to higher-up tables are ignored (they break ties on the second
 * pass — final ordering is otherwise stable alphabetical within a layer).
 *
 * <p>A table reached via {@link CustomScope} also depends on its {@code viaTable}.
 */
public final class TableOrder {

    private TableOrder() {}

    /**
     * Returns the topologically-sorted list of TRACKED tables in {@code tracking}.
     * Cycles are broken by dropping the offending edge and continuing — the caller
     * may have to set FK columns in a second pass when this happens.
     */
    public static List<String> topological(DataTracking tracking) {
        if (tracking.tables() == null) return List.of();

        // Build the dependency graph: for each TRACKED table, which other TRACKED tables must precede it.
        Set<String> tracked = new HashSet<>();
        for (var e : tracking.tables().entrySet()) {
            var t = e.getValue();
            if (t.stationTransfer() != null && t.stationTransfer().status() == Status.TRACKED) {
                tracked.add(e.getKey());
            }
        }

        Map<String, Set<String>> dependsOn = new TreeMap<>();
        for (String name : tracked) dependsOn.put(name, new HashSet<>());

        for (String name : tracked) {
            var entry = tracking.tables().get(name);
            if (entry.foreignKeys() == null) continue;
            for (ForeignKey fk : entry.foreignKeys()) {
                String ref = fk.refTable();
                if (ref == null || ref.equals(name)) continue; // skip self-FK
                // SET NULL FKs are soft dependencies — the importer can leave the column null
                // and patch it in a second pass. Skipping them breaks dependency cycles like
                // station.owner_member_id ↔ station_member.station_id.
                if ("SET NULL".equalsIgnoreCase(fk.onDelete())) continue;
                if (tracked.contains(ref)) dependsOn.get(name).add(ref);
            }
            // customScope is an EXPORT-side concept (it filters which rows belong to a station via
            // a detour through another table). It is NOT an INSERT-order dependency — the referenced
            // FK target still drives import order. Treating it as a dep would create false cycles
            // such as account ←customScope→ station_member.
        }

        return kahnSort(dependsOn);
    }

    private static List<String> kahnSort(Map<String, Set<String>> dependsOn) {
        // Reverse map: who depends on me?
        Map<String, Set<String>> dependents = new TreeMap<>();
        for (String n : dependsOn.keySet()) dependents.put(n, new HashSet<>());
        for (var e : dependsOn.entrySet()) {
            for (String dep : e.getValue()) {
                dependents.computeIfAbsent(dep, k -> new HashSet<>()).add(e.getKey());
            }
        }

        // Track remaining incoming-edge count per node. TreeMap so the cycle-leftover output
        // is alphabetically stable.
        Map<String, Integer> remaining = new TreeMap<>();
        for (var e : dependsOn.entrySet()) {
            remaining.put(e.getKey(), e.getValue().size());
        }

        List<String> result = new ArrayList<>(dependsOn.size());

        // Use a TreeSet-style traversal for stable alphabetical output within a layer.
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

        // Anything remaining is part of an FK cycle; append it in alphabetical order so it
        // still appears in the output. Callers must be prepared to defer the cyclic FKs.
        for (var e : remaining.entrySet()) {
            if (e.getValue() > 0 && !result.contains(e.getKey())) result.add(e.getKey());
        }
        return List.copyOf(result);
    }
}
