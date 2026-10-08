/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.ColumnEntry;
import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.TableEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The SQL that flattens a table's {@link Lookup}s into its exported rows: one selected column and
 * one {@code LEFT JOIN} per lookup, the table itself aliased {@code t} and the lookup targets
 * {@code lk0}, {@code lk1} and so on. The station transfer and the GDPR export both emit it.
 *
 * <p>Every lookup's foreign key is resolved when this is built, so a lookup that names a column
 * without a tracked foreign key fails here with the table's name rather than as a query the
 * database cannot run.
 *
 * <p>A lookup that emits under the name of one of the table's own columns is not joined: the row
 * already carries the value it would pick, such as a borrowed piece naming its owner by uid beside
 * the owner's id, and the import reads it from there.
 *
 * @param lookups the table's lookups that are joined in, in order
 * @param targets the foreign key each of them follows, at the same index
 */
record LookupSql(List<Lookup> lookups, List<ForeignKey> targets) {

    /**
     * Resolves the lookups of {@code table}.
     *
     * @throws IllegalStateException when a lookup follows a column that has no tracked foreign key
     */
    static LookupSql of(String tableName, TableEntry table) {
        Set<String> ownColumns = table.columns().stream().map(ColumnEntry::name).collect(Collectors.toSet());
        List<Lookup> joined = new ArrayList<>();
        List<ForeignKey> targets = new ArrayList<>();
        for (Lookup lookup : lookupsOf(table)) {
            ForeignKey target;
            try {
                target = table.foreignKeyFor(lookup.via());
            } catch (IllegalStateException e) {
                throw new IllegalStateException(
                        "Lookup '" + lookup.emitAs() + "' on table " + tableName + " cannot be followed: "
                                + e.getMessage(),
                        e);
            }
            if (ownColumns.contains(lookup.emitAs())) continue;
            joined.add(lookup);
            targets.add(target);
        }
        return new LookupSql(List.copyOf(joined), List.copyOf(targets));
    }

    /**
     * The lookups of a table. The entry turns a missing list into an empty one when it is built, so
     * this only spells out for the reader what the record's type does not.
     *
     * @param table the table
     * @return its lookups, empty when it has none
     */
    static List<Lookup> lookupsOf(TableEntry table) {
        return Objects.requireNonNullElse(table.lookups(), List.of());
    }

    /** Appends {@code , lk<i>.<pick> AS <emitAs>} for every lookup, after the table's own columns. */
    void appendSelect(StringBuilder sb) {
        for (int i = 0; i < lookups.size(); i++) {
            var lk = lookups.get(i);
            sb.append(", lk")
                    .append(i)
                    .append('.')
                    .append(lk.pick())
                    .append(" AS ")
                    .append(lk.emitAs());
        }
    }

    /** Appends the {@code LEFT JOIN} that reaches the target of every lookup. */
    void appendJoins(StringBuilder sb) {
        for (int i = 0; i < lookups.size(); i++) {
            var fk = targets.get(i);
            sb.append(" LEFT JOIN ")
                    .append(fk.refTable())
                    .append(" lk")
                    .append(i)
                    .append(" ON t.")
                    .append(lookups.get(i).via())
                    .append(" = lk")
                    .append(i)
                    .append('.')
                    .append(fk.refColumn());
        }
    }
}
