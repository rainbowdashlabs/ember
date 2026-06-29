/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.tracking.CustomScope;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.OutputShape;
import dev.chojo.ember.tracking.Status;
import dev.chojo.ember.tracking.TableEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Reads rows from a single tracked table for a given station, using only metadata
 * supplied by {@link DataTracking}.
 *
 * <ul>
 *   <li>{@link TableEntry#stationTransfer() ignoredColumns} controls which columns are emitted.</li>
 *   <li>{@link TableEntry#lookups()} adds FK-flattened fields (e.g. {@code account_email}).</li>
 *   <li>{@link TableEntry#customScope()} overrides scope derivation for tables reached via an
 *       incoming FK (e.g. {@code account} through {@code station_member.account_id}).</li>
 *   <li>{@link TableEntry#outputShape()} reshapes the result into a single object or a flat list.</li>
 * </ul>
 */
public final class GenericTableExporter {

    private final DataTracking tracking;
    private final StationScopeResolver scopeResolver;

    public GenericTableExporter(DataTracking tracking) {
        this.tracking = tracking;
        this.scopeResolver = new StationScopeResolver(tracking);
    }

    private static void appendSelect(StringBuilder sb, List<String> columns, List<Lookup> lookups) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("t.").append(columns.get(i));
        }
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

    private static void appendLookupJoins(StringBuilder sb, TableEntry table, List<Lookup> lookups) {
        for (int i = 0; i < lookups.size(); i++) {
            var lk = lookups.get(i);
            ForeignKey fk = findFk(table, lk.via());
            sb.append(" LEFT JOIN ")
                    .append(fk.refTable())
                    .append(" lk")
                    .append(i)
                    .append(" ON t.")
                    .append(lk.via())
                    .append(" = lk")
                    .append(i)
                    .append('.')
                    .append(fk.refColumn());
        }
    }

    private static void appendOrderAndPagination(StringBuilder sb, List<String> columns) {
        sb.append(" ORDER BY t.").append(columns.get(0)).append(" OFFSET :offset LIMIT :limit");
    }

    private static ForeignKey findFk(TableEntry table, String column) {
        if (table.foreignKeys() != null) {
            for (var fk : table.foreignKeys()) {
                if (column.equals(fk.column())) return fk;
            }
        }
        throw new IllegalStateException(
                "Lookup references FK column '" + column + "' on " + table + " but no such FK is tracked");
    }

    /**
     * Returns the raw rows for {@code tableName} owned by {@code stationId} (no shape transform).
     */
    public List<Map<String, Object>> export(String tableName, int stationId, int offset, int limit) {
        var table = tableEntry(tableName);
        var transfer = table.stationTransfer();
        if (transfer == null || transfer.status() != Status.TRACKED) {
            throw new IllegalStateException("Table " + tableName + " is not TRACKED for station transfer (status="
                    + (transfer == null ? "null" : transfer.status()) + ")");
        }

        Set<String> ignored = Set.copyOf(transfer.ignoredColumns() == null ? List.of() : transfer.ignoredColumns());
        List<String> selectableColumns = new ArrayList<>();
        for (var col : table.columns()) {
            if (!ignored.contains(col.name())) selectableColumns.add(col.name());
        }
        if (selectableColumns.isEmpty()) {
            throw new IllegalStateException("Table " + tableName + " has no exportable columns after ignoredColumns");
        }

        List<Lookup> lookups = table.lookups() == null ? List.of() : table.lookups();
        String sql = table.customScope() != null
                ? buildCustomScopeSql(table, tableName, selectableColumns, lookups, table.customScope())
                : buildDirectScopeSql(table, tableName, selectableColumns, lookups);
        return runQuery(sql, stationId, offset, limit);
    }

    /**
     * Returns rows reshaped according to the table's {@link OutputShape}:
     * a {@code List<Map>} for {@code ROWS}, a single {@code Map} (or null) for {@code SINGLE},
     * a {@code List<Object>} for {@code FLAT}.
     */
    public Object exportShaped(String tableName, int stationId, int offset, int limit) {
        var table = tableEntry(tableName);
        OutputShape shape = table.effectiveShape();
        return switch (shape) {
            case ROWS -> export(tableName, stationId, offset, limit);
            case SINGLE -> {
                var rows = export(tableName, stationId, 0, 1);
                yield rows.isEmpty() ? null : rows.getFirst();
            }
            case FLAT -> {
                String field = table.flatField();
                if (field == null) {
                    throw new IllegalStateException(
                            "Table " + tableName + " has outputShape=FLAT but no flatField configured");
                }
                var rows = export(tableName, stationId, offset, limit);
                yield rows.stream().map(r -> r.get(field)).toList();
            }
        };
    }

    private TableEntry tableEntry(String tableName) {
        var t = tracking.tables() == null ? null : tracking.tables().get(tableName);
        if (t == null) throw new IllegalArgumentException("Unknown table: " + tableName);
        return t;
    }

    private String buildDirectScopeSql(TableEntry table, String tableName, List<String> columns, List<Lookup> lookups) {
        var scope = scopeResolver
                .resolve(tableName)
                .orElseThrow(() ->
                        new IllegalStateException("No station scope path could be derived for table " + tableName));

        var sb = new StringBuilder("SELECT ");
        appendSelect(sb, columns, lookups);
        sb.append(" FROM ").append(tableName).append(" t");

        Map<String, String> tableAlias = new LinkedHashMap<>();
        tableAlias.put(tableName, "t");
        int aliasIdx = 0;
        for (var join : scope.joins()) {
            String fromAlias = tableAlias.get(join.from());
            String newAlias = "s" + aliasIdx++;
            tableAlias.put(join.fk().refTable(), newAlias);
            sb.append(" JOIN ")
                    .append(join.fk().refTable())
                    .append(' ')
                    .append(newAlias)
                    .append(" ON ")
                    .append(fromAlias)
                    .append('.')
                    .append(join.fk().column())
                    .append(" = ")
                    .append(newAlias)
                    .append('.')
                    .append(join.fk().refColumn());
        }
        appendLookupJoins(sb, table, lookups);
        sb.append(" WHERE ")
                .append(tableAlias.get(scope.terminalTable()))
                .append('.')
                .append(scope.scopeColumn())
                .append(" = :stationId");
        appendOrderAndPagination(sb, columns);
        return sb.toString();
    }

    private String buildCustomScopeSql(
            TableEntry table, String tableName, List<String> columns, List<Lookup> lookups, CustomScope customScope) {
        var sb = new StringBuilder("SELECT ");
        appendSelect(sb, columns, lookups);
        sb.append(" FROM ").append(tableName).append(" t");
        appendLookupJoins(sb, table, lookups);
        sb.append(" WHERE ").append(buildCustomScopeFilter(customScope, "t", 0));
        appendOrderAndPagination(sb, columns);
        return sb.toString();
    }

    /**
     * Renders the {@code IN (SELECT …)} filter that scopes {@code parentAlias.<refColumn>} via
     * the {@code customScope}'s viaTable. Recursive: when {@code viaTable} itself has a custom
     * scope (e.g. {@code federation_lending_message} → {@code federation_lending_request} →
     * {@code station}), the inner query nests through the chain. Otherwise the inner query
     * joins through the viaTable's FK-resolved scope path.
     *
     * @param depth nesting depth, used to generate non-colliding aliases for the recursive case
     */
    private String buildCustomScopeFilter(CustomScope customScope, String parentAlias, int depth) {
        String vt = "vt" + depth;
        var sb = new StringBuilder();
        sb.append(parentAlias).append('.').append(customScope.refColumn()).append(" IN (SELECT ");
        if (customScope.distinct()) sb.append("DISTINCT ");
        sb.append(vt).append('.').append(customScope.viaColumn());
        sb.append(" FROM ").append(customScope.viaTable()).append(' ').append(vt);

        var via = tracking.tables() == null ? null : tracking.tables().get(customScope.viaTable());
        if (via != null && via.customScope() != null) {
            sb.append(" WHERE ").append(buildCustomScopeFilter(via.customScope(), vt, depth + 1));
            sb.append(" AND ")
                    .append(vt)
                    .append('.')
                    .append(customScope.viaColumn())
                    .append(" IS NOT NULL)");
            return sb.toString();
        }

        var viaScope = scopeResolver
                .resolve(customScope.viaTable())
                .orElseThrow(() -> new IllegalStateException(
                        "customScope.viaTable " + customScope.viaTable() + " is not station-scoped"));
        Map<String, String> vAlias = new LinkedHashMap<>();
        vAlias.put(customScope.viaTable(), vt);
        int aliasIdx = 0;
        for (var join : viaScope.joins()) {
            String fromAlias = vAlias.get(join.from());
            String newAlias = "vs" + depth + "_" + aliasIdx++;
            vAlias.put(join.fk().refTable(), newAlias);
            sb.append(" JOIN ")
                    .append(join.fk().refTable())
                    .append(' ')
                    .append(newAlias)
                    .append(" ON ")
                    .append(fromAlias)
                    .append('.')
                    .append(join.fk().column())
                    .append(" = ")
                    .append(newAlias)
                    .append('.')
                    .append(join.fk().refColumn());
        }
        sb.append(" WHERE ")
                .append(vAlias.get(viaScope.terminalTable()))
                .append('.')
                .append(viaScope.scopeColumn())
                .append(" = :stationId AND ")
                .append(vt)
                .append('.')
                .append(customScope.viaColumn())
                .append(" IS NOT NULL)");
        return sb.toString();
    }

    private List<Map<String, Object>> runQuery(String sql, int stationId, int offset, int limit) {
        var queryObj = query(sql)
                .single(call().bind("stationId", stationId)
                        .bind("offset", offset)
                        .bind("limit", limit));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) (List<?>) queryObj.map(row -> {
                    var meta = row.getMetaData();
                    var out = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String typeName = meta.getColumnTypeName(i);
                        String label = meta.getColumnLabel(i);
                        if ("jsonb".equals(typeName) || "json".equals(typeName)) {
                            out.put(label, row.getString(i));
                        } else if ("timestamptz".equals(typeName)
                                || "timestamp".equals(typeName)
                                || "timestamp with time zone".equals(typeName)
                                || "timestamp without time zone".equals(typeName)) {
                            // Wire format for timestamps is epoch milliseconds (long). Matches
                            // GenericTableImporter.asInstant which already treats Number values as
                            // epoch millis, and dodges JSON / locale / PG-version parsing edge
                            // cases that a string ISO-8601 format runs into on the import side.
                            // Routes through the SADU value converter rather than getObject(Instant)
                            // because the JDBC driver's java.time mapping is not guaranteed across
                            // every PG / driver version we ship against.
                            Instant instant = row.get(i, StandardValueConverter.INSTANT_TIMESTAMP);
                            out.put(label, instant == null ? null : instant.toEpochMilli());
                        } else {
                            out.put(label, row.getObject(i));
                        }
                    }
                    return out;
                })
                .all();
        return rows;
    }
}
