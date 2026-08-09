/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Shared helpers for the repository layer, factoring out the query shapes that otherwise
 * repeat across every repository: find-by-id, insert-returning, delete-by-id, scalar
 * exists/count reads, and position reordering. All {@code table}/{@code columns} arguments
 * must be trusted compile-time constants — never user input; values are always bound via
 * named parameters on the passed {@link Call}.
 */
public final class SqlSupport {
    private static final Pattern PLAIN_COLUMN = Pattern.compile("[a-z_][a-z0-9_]*");

    private SqlSupport() {}

    /**
     * Loads a single row by primary key using an explicit column list, mapped via the
     * entity's row mapping.
     */
    public static <T> Optional<T> findById(String table, String columns, int id, RowMapping<T> mapper) {
        return query("SELECT %s FROM %s WHERE id = :id;", columns, table)
                .single(call().bind("id", id))
                .map(mapper)
                .first();
    }

    /**
     * Runs an {@code INSERT ... RETURNING} (or any single-row-returning statement) and maps
     * the returned row, throwing when the database unexpectedly returns nothing. Trailing
     * arguments are format parameters for {@code %s} fragments in the statement.
     */
    public static <T> T insertReturning(String sql, Call call, RowMapping<T> mapper, Object... sqlFormat) {
        return query(sql, sqlFormat).single(call).map(mapper).first().orElseThrow();
    }

    /**
     * Deletes a single row by primary key, reporting whether a row was removed.
     */
    public static boolean deleteById(String table, int id) {
        return query("DELETE FROM %s WHERE id = :id;", table)
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Evaluates an existence query — any statement of the shape {@code SELECT 1 ... LIMIT 1} —
     * to a boolean without the per-repository {@code map(row -> true)} idiom.
     */
    public static boolean exists(String sql, Call call, Object... sqlFormat) {
        return query(sql, sqlFormat).single(call).map(row -> true).first().orElse(false);
    }

    /**
     * Evaluates a scalar count query. The statement must select exactly one integer column;
     * the value is read by position, so no column alias is required. Absent rows count as zero.
     */
    public static int count(String sql, Call call, Object... sqlFormat) {
        return query(sql, sqlFormat)
                .single(call)
                .map(row -> row.getInt(1))
                .first()
                .orElse(0);
    }

    /**
     * Rewrites {@code positionColumn} for every row of {@code table} named in
     * {@code orderedIds} to its zero-based index in the list, scoped by
     * {@code scopeColumn = scopeId} so ids from foreign scopes are ignored. Runs as two
     * set-based statements — first parking the rows on distinct negative positions, then
     * landing them on their final ones — to satisfy {@code UNIQUE(scope, position)}
     * constraints that would reject a direct permutation.
     */
    public static void reorder(
            String table, String positionColumn, String scopeColumn, int scopeId, List<Integer> orderedIds) {
        if (orderedIds.isEmpty()) return;
        query("""
                        UPDATE %1$s t
                           SET %2$s = -o.ord
                          FROM unnest(:ids::INT[]) WITH ORDINALITY AS o(id, ord)
                         WHERE t.id = o.id
                           AND t.%3$s = :scope;""", table, positionColumn, scopeColumn)
                .single(call().bind("ids", orderedIds, PostgreSqlTypes.INTEGER).bind("scope", scopeId))
                .update();
        query("""
                        UPDATE %1$s t
                           SET %2$s = o.ord - 1
                          FROM unnest(:ids::INT[]) WITH ORDINALITY AS o(id, ord)
                         WHERE t.id = o.id
                           AND t.%3$s = :scope;""", table, positionColumn, scopeColumn)
                .single(call().bind("ids", orderedIds, PostgreSqlTypes.INTEGER).bind("scope", scopeId))
                .update();
    }

    /**
     * Prefixes every plain column name in a comma-separated column list with a table alias,
     * so a single bare column constant can serve both aliased joins and
     * {@code INSERT ... RETURNING}. Entries that are not plain identifiers (expressions,
     * already-qualified or aliased columns) are passed through untouched; commas inside
     * parentheses do not split.
     */
    public static String alias(String prefix, String columns) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < columns.length(); i++) {
            char c = columns.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                parts.add(columns.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(columns.substring(start));
        return parts.stream()
                .map(String::strip)
                .map(col -> PLAIN_COLUMN.matcher(col).matches() ? prefix + "." + col : col)
                .collect(Collectors.joining(", "));
    }
}
