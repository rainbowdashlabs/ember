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
import dev.chojo.ember.tracking.GdprExportContext;
import dev.chojo.ember.tracking.IdentityColumn;
import dev.chojo.ember.tracking.IdentityType;
import dev.chojo.ember.tracking.TableEntry;
import dev.chojo.ember.tracking.TrackingStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Tables whose {@code gdprExport.status} is {@link TrackingStatus#IGNORED} or {@link TrackingStatus#UNVERIFIED}
 * are skipped, as are tables whose {@code identityColumns} list is empty (those are linked through
 * a parent row - the caller should chain them).
 */
public final class GenericGdprExporter {

    private final DataTracking tracking;

    public GenericGdprExporter(DataTracking tracking) {
        this.tracking = tracking;
    }

    /**
     * Returns the identity columns on {@code ctx} whose {@link IdentityType} matches {@code type}
     * AND that actually exist on the table. The schema-mismatch check guards against drift.
     */
    private static List<IdentityColumn> matchingIdentityColumns(
            GdprExportContext ctx, IdentityType type, TableEntry table) {
        List<IdentityColumn> result = new ArrayList<>();
        Set<String> known = new java.util.HashSet<>();
        for (var c : table.columns()) known.add(c.name());
        for (var ic : Objects.requireNonNullElse(ctx.identityColumns(), List.<IdentityColumn>of())) {
            if (ic.type() == type && known.contains(ic.column())) result.add(ic);
        }
        return result;
    }

    /**
     * The select for one table's rows of this identity. A uuid identity is cast in the SQL, since the
     * driver binds the parameter as varchar and the database refuses to compare that with a uuid.
     */
    private static String buildSelectSql(
            TableEntry table,
            String tableName,
            List<IdentityColumn> matching,
            GdprExportContext ctx,
            IdentityType type) {
        Set<String> ignored = Set.copyOf(ctx.ignoredColumns());
        var lookups = LookupSql.of(tableName, table);

        var sb = new StringBuilder("SELECT ");
        boolean firstCol = true;
        for (ColumnEntry col : table.columns()) {
            if (ignored.contains(col.name())) continue;
            if (!firstCol) sb.append(", ");
            firstCol = false;
            sb.append("t.").append(col.name());
        }

        lookups.appendSelect(sb);
        sb.append(" FROM ").append(tableName).append(" t");
        lookups.appendJoins(sb);

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

    /**
     * Returns every TRACKED row matching the given identity. Keyed by table name; the value is the
     * list of rows (column → value). Tables without any matching identity column for {@code type}
     * are skipped.
     */
    public Map<String, List<Map<String, Object>>> exportByIdentity(IdentityType type, Object identityValue) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (var entry : tracking.tables().entrySet()) {
            String tableName = entry.getKey();
            TableEntry table = entry.getValue();
            GdprExportContext ctx = table.gdprExport();
            if (ctx == null || ctx.status() != TrackingStatus.TRACKED) continue;

            var matching = matchingIdentityColumns(ctx, type, table);
            if (matching.isEmpty()) continue;

            String sql = buildSelectSql(table, tableName, matching, ctx, type);
            List<Map<String, Object>> rows = runQuery(sql, type, identityValue);
            if (!rows.isEmpty()) result.put(tableName, rows);
        }
        return result;
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
                        } else if (ArrayValues.isArray(typeName)) {
                            out.put(meta.getColumnLabel(i), ArrayValues.read(row.getArray(i)));
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
