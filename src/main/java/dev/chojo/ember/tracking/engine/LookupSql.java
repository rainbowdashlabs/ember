/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.TableEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * The SQL that flattens a table's {@link Lookup}s into its exported rows: one selected column and
 * one {@code LEFT JOIN} per lookup, the table itself aliased {@code t} and the lookup targets
 * {@code lk0}, {@code lk1} and so on. The station transfer and the GDPR export both emit it.
 *
 * <p>Every lookup's foreign key is resolved when this is built, so a lookup that names a column
 * without a tracked foreign key fails here with the table's name rather than as a query the
 * database cannot run.
 *
 * @param lookups the table's lookups, in order
 * @param targets the foreign key each lookup follows, at the same index
 */
record LookupSql(List<Lookup> lookups, List<ForeignKey> targets) {

    /**
     * Resolves the lookups of {@code table}.
     *
     * @throws IllegalStateException when a lookup follows a column that has no tracked foreign key
     */
    static LookupSql of(String tableName, TableEntry table) {
        List<ForeignKey> targets = new ArrayList<>();
        for (Lookup lookup : table.lookups()) {
            try {
                targets.add(table.foreignKeyFor(lookup.via()));
            } catch (IllegalStateException e) {
                throw new IllegalStateException(
                        "Lookup '" + lookup.emitAs() + "' on table " + tableName + " cannot be followed: "
                                + e.getMessage(),
                        e);
            }
        }
        return new LookupSql(table.lookups(), List.copyOf(targets));
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
