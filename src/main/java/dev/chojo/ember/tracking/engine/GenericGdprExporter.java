/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.tracking.ColumnEntry;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.GdprExportContext;
import dev.chojo.ember.tracking.IdentityColumn;
import dev.chojo.ember.tracking.IdentityType;
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.Status;
import dev.chojo.ember.tracking.TableEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Builds GDPR data-export queries from {@code data_tracking.json}. For every TRACKED
 * {@link GdprExportContext} entry whose {@link IdentityColumn#type()} matches the requested identity,
 * a {@code SELECT} is emitted with a {@code WHERE} fragment combining the identity column(s)
 * with {@code OR}.
 *
 * <p>The resulting payload is a map keyed by DB table name; consumers can wrap it for output.
 * Tables whose {@code gdprExport.status} is {@link Status#IGNORED} or {@link Status#UNVERIFIED}
 * are skipped, as are tables whose {@code identityColumns} list is empty (those are linked through
 * a parent row — the caller should chain them).
 */
public final class GenericGdprExporter {

    private final DataTracking tracking;

    public GenericGdprExporter(DataTracking tracking) {
        this.tracking = tracking;
    }

    /**
     * Returns every TRACKED row matching the given identity. Keyed by table name; the value is the
     * list of rows (column → value). Tables without any matching identity column for {@code type}
     * are skipped.
     */
    public Map<String, List<Map<String, Object>>> exportByIdentity(IdentityType type, Object identityValue) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (tracking.tables() == null) return result;

        for (var entry : tracking.tables().entrySet()) {
            String tableName = entry.getKey();
            TableEntry table = entry.getValue();
            GdprExportContext ctx = table.gdprExport();
            if (ctx == null || ctx.status() != Status.TRACKED) continue;

            var matching = matchingIdentityColumns(ctx, type, table);
            if (matching.isEmpty()) continue;

            String sql = buildSelectSql(table, tableName, matching, ctx, type);
            List<Map<String, Object>> rows = runQuery(sql, type, identityValue);
            if (!rows.isEmpty()) result.put(tableName, rows);
        }
        return result;
    }

    /**
     * Returns the identity columns on {@code ctx} whose {@link IdentityType} matches {@code type}
     * AND that actually exist on the table. The schema-mismatch check guards against drift.
     */
    private static List<IdentityColumn> matchingIdentityColumns(
            GdprExportContext ctx, IdentityType type, TableEntry table) {
        if (ctx.identityColumns() == null) return List.of();
        List<IdentityColumn> result = new ArrayList<>();
        Set<String> known = new java.util.HashSet<>();
        if (table.columns() != null) for (var c : table.columns()) known.add(c.name());
        for (var ic : ctx.identityColumns()) {
            if (ic.type() == type && known.contains(ic.column())) result.add(ic);
        }
        return result;
    }

    private static String buildSelectSql(
            TableEntry table,
            String tableName,
            List<IdentityColumn> matching,
            GdprExportContext ctx,
            IdentityType type) {
        Set<String> ignored = Set.copyOf(ctx.ignoredColumns() == null ? List.of() : ctx.ignoredColumns());

        var sb = new StringBuilder("SELECT ");
        boolean firstCol = true;
        for (ColumnEntry col : table.columns()) {
            if (ignored.contains(col.name())) continue;
            if (!firstCol) sb.append(", ");
            firstCol = false;
            sb.append("t.").append(col.name());
        }

        // Optional Lookup-flattened columns (account_email etc.) — same as the transfer exporter.
        List<Lookup> lookups = table.lookups() == null ? List.of() : table.lookups();
        for (int i = 0; i < lookups.size(); i++) {
            var lk = lookups.get(i);
            sb.append(", lk")
                    .append(i)
                    .append('.')
                    .append(lk.pick())
                    .append(" AS ")
                    .append(lk.emitAs());
        }

        sb.append(" FROM ").append(tableName).append(" t");

        for (int i = 0; i < lookups.size(); i++) {
            var lk = lookups.get(i);
            ForeignKey fk = findFk(table, lk.via());
            if (fk == null) continue;
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

        // UUID columns require an explicit cast on the bind, otherwise the JDBC parameter is
        // treated as varchar and PG rejects the comparison.
        String cast = type == IdentityType.MEMBER_UID ? "::uuid" : "";

        sb.append(" WHERE ");
        for (int i = 0; i < matching.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("t.").append(matching.get(i).column()).append(" = :id").append(cast);
            String filter = matching.get(i).filter();
            if (filter != null && !filter.isBlank())
                sb.append(" AND (").append(filter).append(')');
        }
        sb.append(';');
        return sb.toString();
    }

    private static ForeignKey findFk(TableEntry table, String column) {
        if (table.foreignKeys() != null) {
            for (var fk : table.foreignKeys()) if (column.equals(fk.column())) return fk;
        }
        return null;
    }

    private List<Map<String, Object>> runQuery(String sql, IdentityType type, Object identityValue) {
        Call c = call();
        c = switch (type) {
            case ACCOUNT_ID, MEMBER_ID -> {
                Integer i =
                        identityValue instanceof Number n ? n.intValue() : Integer.parseInt(identityValue.toString());
                yield c.bind("id", i);
            }
            case MEMBER_UID -> {
                UUID uid = identityValue instanceof UUID u ? u : UUID.fromString(identityValue.toString());
                yield c.bind("id", uid, StandardValueConverter.UUID_STRING);
            }
        };
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) (List<?>) query(sql)
                .single(c)
                .map(row -> {
                    var meta = row.getMetaData();
                    var out = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String typeName = meta.getColumnTypeName(i);
                        if ("jsonb".equals(typeName) || "json".equals(typeName)) {
                            out.put(meta.getColumnLabel(i), row.getString(i));
                        } else {
                            out.put(meta.getColumnLabel(i), row.getObject(i));
                        }
                    }
                    return out;
                })
                .all();
        return rows;
    }
}
