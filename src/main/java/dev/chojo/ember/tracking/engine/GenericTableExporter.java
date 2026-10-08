/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.tracking.CustomScope;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.OutputShape;
import dev.chojo.ember.tracking.TableEntry;
import dev.chojo.ember.tracking.TrackingStatus;
import org.jspecify.annotations.Nullable;

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

    private static void appendSelect(StringBuilder sb, List<String> columns, LookupSql lookups) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("t.").append(columns.get(i));
        }
        lookups.appendSelect(sb);
    }

    private static void appendOrderAndPagination(StringBuilder sb, List<String> columns) {
        sb.append(" ORDER BY t.").append(columns.get(0)).append(" OFFSET :offset LIMIT :limit");
    }

    /**
     * Returns the raw rows for {@code tableName} owned by {@code stationId} (no shape transform).
     */
    public List<Map<String, Object>> export(String tableName, int stationId, int offset, int limit) {
        var table = tableEntry(tableName);
        var transfer = table.stationTransfer();
        if (transfer == null || transfer.status() != TrackingStatus.TRACKED) {
            throw new IllegalStateException("Table " + tableName + " is not TRACKED for station transfer (status="
                    + (transfer == null ? "null" : transfer.status()) + ")");
        }

        Set<String> ignored = Set.copyOf(transfer.ignoredColumns());
        List<String> selectableColumns = new ArrayList<>();
        for (var col : table.columns()) {
            if (!ignored.contains(col.name())) selectableColumns.add(col.name());
        }
        if (selectableColumns.isEmpty()) {
            throw new IllegalStateException("Table " + tableName + " has no exportable columns after ignoredColumns");
        }

        var lookups = LookupSql.of(tableName, table);
        var customScope = table.customScope();
        String sql = customScope != null
                ? buildCustomScopeSql(tableName, selectableColumns, lookups, customScope)
                : buildDirectScopeSql(tableName, selectableColumns, lookups);
        return runQuery(sql, stationId, offset, limit);
    }

    /**
     * Returns rows reshaped according to the table's {@link OutputShape}:
     * a {@code List<Map>} for {@code ROWS}, a single {@code Map} (or null) for {@code SINGLE},
     * a {@code List<Object>} for {@code FLAT}.
     */
    public @Nullable Object exportShaped(String tableName, int stationId, int offset, int limit) {
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
        var t = tracking.tables().get(tableName);
        if (t == null) throw new IllegalArgumentException("Unknown table: " + tableName);
        return t;
    }

    /**
     * The page query for a table scoped through its foreign keys. A single path is joined in; several
     * paths, one per column that may be empty, are each joined in on the side and a row is the station's
     * when any of them reaches it.
     */
    private String buildDirectScopeSql(String tableName, List<String> columns, LookupSql lookups) {
        var scopes = scopeResolver.resolveAll(tableName);
        if (scopes.isEmpty()) {
            throw new IllegalStateException("No station scope path could be derived for table " + tableName);
        }
        String joinKind = scopes.size() == 1 ? " JOIN " : " LEFT JOIN ";

        var sb = new StringBuilder("SELECT ");
        appendSelect(sb, columns, lookups);
        sb.append(" FROM ").append(tableName).append(" t");

        List<String> conditions = new ArrayList<>();
        int aliasIdx = 0;
        for (var scope : scopes) {
            Map<String, String> tableAlias = new LinkedHashMap<>();
            tableAlias.put(tableName, "t");
            for (var join : scope.joins()) {
                String fromAlias = tableAlias.get(join.from());
                String newAlias = "s" + aliasIdx++;
                tableAlias.put(join.fk().refTable(), newAlias);
                sb.append(joinKind)
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
            conditions.add(tableAlias.get(scope.terminalTable()) + "." + scope.scopeColumn() + " = :stationId");
        }
        lookups.appendJoins(sb);
        sb.append(" WHERE (").append(String.join(" OR ", conditions)).append(')');
        appendOrderAndPagination(sb, columns);
        return sb.toString();
    }

    private String buildCustomScopeSql(
            String tableName, List<String> columns, LookupSql lookups, CustomScope customScope) {
        var sb = new StringBuilder("SELECT ");
        appendSelect(sb, columns, lookups);
        sb.append(" FROM ").append(tableName).append(" t");
        lookups.appendJoins(sb);
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

        var via = tracking.tables().get(customScope.viaTable());
        CustomScope nestedScope = via != null ? via.customScope() : null;
        if (nestedScope != null) {
            sb.append(" WHERE ").append(buildCustomScopeFilter(nestedScope, vt, depth + 1));
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

    /**
     * Runs the page query and maps each row to its wire form.
     *
     * <p>Timestamps travel as epoch milliseconds, which the importer already reads and which avoids the
     * parsing edge cases of a string format. They are read through the value converter rather than the
     * driver's own date mapping, which is not the same across every driver version shipped against.
     * Arrays travel as the list of their elements, see {@link ArrayValues}.
     */
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
                            Instant instant = row.get(i, StandardValueConverter.INSTANT_TIMESTAMP);
                            out.put(label, instant == null ? null : instant.toEpochMilli());
                        } else if (ArrayValues.isArray(typeName)) {
                            out.put(label, ArrayValues.read(row.getArray(i)));
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
