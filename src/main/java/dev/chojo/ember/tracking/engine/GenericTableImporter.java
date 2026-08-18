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
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.TableEntry;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Imports rows for a TRACKED table back into the database using only metadata from
 * {@link DataTracking}.
 *
 * <p>Each row goes through this pipeline:
 * <ul>
 *   <li>{@code station_id} is set from the import context.</li>
 *   <li>FK columns to other tracked tables are remapped via {@link IdRemapper} keyed
 *       by the referenced table name.</li>
 *   <li>{@link Lookup}-emitted fields (e.g. {@code account_email}) are resolved back into
 *       FK target IDs by querying the referenced table.</li>
 *   <li>Other non-ignored columns are bound as-is, with {@code jsonb}/{@code uuid}/
 *       {@code timestamptz}/{@code bytea} bindings inferred from {@link ColumnEntry#type()}.</li>
 *   <li>Tables with an {@code id} integer PK use {@code RETURNING id} so the
 *       source → target mapping is tracked for downstream tables. Tables without one (junction
 *       tables) insert with {@code ON CONFLICT DO NOTHING}.</li>
 * </ul>
 */
public final class GenericTableImporter {

    private final DataTracking tracking;

    public GenericTableImporter(DataTracking tracking) {
        this.tracking = tracking;
    }

    /**
     * Looks up an id in {@code table} where {@code column = value}. Returns null when no row
     * matches. Adds a {@code ::uuid} cast to the bound value whenever the target column type is
     * declared as {@code uuid} in the tracking config - Postgres rejects a {@code uuid = text}
     * comparison without the cast.
     */
    private Integer resolveByColumn(String table, String column, Object value) {
        String columnType = lookupColumnType(table, column);
        String cast = "uuid".equalsIgnoreCase(columnType) ? "::uuid" : "";
        return query("SELECT id FROM " + table + " WHERE " + column + " = :v" + cast + " LIMIT 1;")
                .single(call().bind("v", value == null ? null : value.toString()))
                .map(row -> row.getInt("id"))
                .first()
                .orElse(null);
    }

    private String lookupColumnType(String tableName, String columnName) {
        var t = tracking.tables() == null ? null : tracking.tables().get(tableName);
        if (t == null || t.columns() == null) return null;
        for (ColumnEntry col : t.columns()) {
            if (col.name().equals(columnName)) return col.type();
        }
        return null;
    }

    private static ForeignKey findFk(TableEntry table, String column) {
        if (table.foreignKeys() != null) {
            for (var fk : table.foreignKeys()) if (column.equals(fk.column())) return fk;
        }
        return null;
    }

    private static ColumnEntry findColumn(TableEntry table, String column) {
        if (table.columns() != null) {
            for (var c : table.columns()) if (column.equals(c.name())) return c;
        }
        return null;
    }

    private static boolean hasIntegerIdPk(TableEntry table) {
        var col = findColumn(table, "id");
        return col != null && ("int4".equals(col.type()) || "int8".equals(col.type()));
    }

    /**
     * Treat tsvector-style derived columns as DB-maintained and never INSERTed by us.
     */
    private static boolean isGeneratedColumn(ColumnEntry col) {
        return "tsvector".equals(col.type());
    }

    private static Integer toInteger(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String buildInsertSql(String tableName, Map<String, BoundValue> bind, boolean hasIdPk) {
        if (bind.isEmpty()) return "SELECT 1;"; // shouldn't happen for real tables
        var cols = new StringBuilder();
        var vals = new StringBuilder();
        boolean first = true;
        for (var e : bind.entrySet()) {
            if (!first) {
                cols.append(", ");
                vals.append(", ");
            }
            first = false;
            cols.append(e.getKey());
            vals.append(':').append(e.getKey()).append(castFor(e.getValue().type()));
        }
        var sql = new StringBuilder("INSERT INTO ").append(tableName);
        sql.append('(').append(cols).append(") VALUES(").append(vals).append(')');
        sql.append(" ON CONFLICT DO NOTHING");
        if (hasIdPk) sql.append(" RETURNING id");
        sql.append(';');
        return sql.toString();
    }

    private static String castFor(String type) {
        if (type == null) return "";
        return switch (type) {
            case "jsonb", "json" -> "::jsonb";
            case "uuid" -> "::uuid";
            case "date" -> "::date";
            default -> "";
        };
    }

    private static Integer executeInsertReturningId(String sql, Map<String, BoundValue> bind) {
        return query(sql)
                .single(callFor(bind))
                .map(row -> row.getInt("id"))
                .first()
                .orElse(null);
    }

    private static void executeInsert(String sql, Map<String, BoundValue> bind) {
        query(sql).single(callFor(bind)).insert();
    }

    private static Call callFor(Map<String, BoundValue> bind) {
        Call c = call();
        for (var e : bind.entrySet()) {
            c = bindOne(c, e.getKey(), e.getValue());
        }
        return c;
    }

    private static Call bindOne(Call c, String name, BoundValue bv) {
        // Null values were filtered out before reaching this point - see tryBindRow comments.
        Object val = bv.value();
        String type = bv.type();
        return switch (type == null ? "" : type) {
            // uuid + jsonb take string bindings; the cast lives in the SQL (see castFor).
            case "timestamptz", "timestamp" -> c.bind(name, asInstant(val), StandardValueConverter.INSTANT_TIMESTAMP);
            case "bytea" -> c.bind(name, asBytes(val));
            case "int4", "int8" -> {
                Integer i = toInteger(val);
                yield i == null ? c.bind(name, (Integer) null) : c.bind(name, i);
            }
            case "numeric", "float4", "float8" -> {
                Double d = toDouble(val);
                yield d == null ? c.bind(name, (Double) null) : c.bind(name, d);
            }
            case "bool" -> c.bind(name, asBool(val));
            default -> c.bind(name, val.toString());
        };
    }

    private static Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Instant asInstant(Object val) {
        if (val instanceof Instant i) return i;
        if (val instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (val instanceof java.util.Date d) return d.toInstant();
        if (val instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (val instanceof java.time.LocalDateTime ldt)
            return ldt.atZone(java.time.ZoneOffset.UTC).toInstant();
        if (val instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        if (val instanceof String s && !s.isBlank()) return Instant.parse(s);
        return null;
    }

    private static byte[] asBytes(Object val) {
        if (val instanceof byte[] b) return b;
        if (val instanceof String s) return Base64.getDecoder().decode(s);
        return null;
    }

    private static Boolean asBool(Object val) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    /**
     * Imports the given rows for {@code tableName}.
     *
     * @return number of rows actually inserted (rows whose FK remap could not be resolved are skipped)
     */
    public int importRows(int stationId, String tableName, List<Map<String, Object>> rows, IdRemapper idMap) {
        TableEntry table = tableEntry(tableName);
        Set<String> ignored = Set.copyOf(
                table.stationTransfer().ignoredColumns() == null
                        ? List.of()
                        : table.stationTransfer().ignoredColumns());
        boolean hasIdPk = hasIntegerIdPk(table);

        int imported = 0;
        for (Map<String, Object> row : rows) {
            Integer sourceId = toInteger(row.get("id"));
            Map<String, BoundValue> bind = new LinkedHashMap<>();

            if (!tryBindRow(table, tableName, row, stationId, ignored, idMap, bind)) {
                continue; // unresolvable FK - skip
            }

            String sql = buildInsertSql(tableName, bind, hasIdPk);
            if (hasIdPk) {
                Integer newId = executeInsertReturningId(sql, bind);
                if (newId != null && sourceId != null) idMap.put(tableName, sourceId, newId);
            } else {
                executeInsert(sql, bind);
            }
            imported++;
        }
        return imported;
    }

    private TableEntry tableEntry(String tableName) {
        var t = tracking.tables() == null ? null : tracking.tables().get(tableName);
        if (t == null) throw new IllegalArgumentException("Unknown table: " + tableName);
        return t;
    }

    /**
     * Populates {@code bind} for the given row. Returns {@code false} when a required FK can't be
     * remapped, signalling the caller to skip this row.
     */
    private boolean tryBindRow(
            TableEntry table,
            String tableName,
            Map<String, Object> row,
            int stationId,
            Set<String> ignored,
            IdRemapper idMap,
            Map<String, BoundValue> bind) {
        // 1. station_id from context (if the table has it).
        ColumnEntry stationIdCol = findColumn(table, "station_id");
        if (stationIdCol != null) {
            bind.put("station_id", new BoundValue(stationId, "int4"));
        }

        // 2. FK columns: remap via IdRemapper.
        if (table.foreignKeys() != null) {
            for (ForeignKey fk : table.foreignKeys()) {
                if ("station_id".equals(fk.column())) continue;
                Object sourceVal = row.get(fk.column());
                if (sourceVal == null) continue;
                Integer src = toInteger(sourceVal);
                if (src == null) continue;
                Integer mapped = idMap.get(fk.refTable(), src);
                if (mapped == null || mapped <= 0) {
                    // Try lookup-emitted alternative (e.g. account_email) before giving up.
                    Integer viaLookup = tryResolveViaLookup(table, fk.column(), row);
                    if (viaLookup != null) {
                        bind.put(fk.column(), new BoundValue(viaLookup, "int4"));
                        continue;
                    }
                    return false;
                }
                bind.put(fk.column(), new BoundValue(mapped, "int4"));
            }
        }

        // 3. Lookup-driven FKs (column is ignored, but lookup emit field carries the lookup key).
        if (table.lookups() != null) {
            for (Lookup lk : table.lookups()) {
                if (bind.containsKey(lk.via())) continue; // already resolved above
                Object pickedValue = row.get(lk.emitAs());
                if (pickedValue == null) continue;
                ForeignKey fk = findFk(table, lk.via());
                if (fk == null) continue;
                Integer resolvedId = resolveByColumn(fk.refTable(), lk.pick(), pickedValue);
                if (resolvedId != null) {
                    bind.put(lk.via(), new BoundValue(resolvedId, "int4"));
                }
            }
        }

        // 4. Remaining writable columns. Null source values are skipped entirely so the column is
        // omitted from the INSERT - PostgreSQL fills in NULL (for nullable columns) or the column
        // default (for non-null columns with a DEFAULT). Binding a typed null through JDBC would
        // otherwise be coerced to varchar and PG would reject it against int/jsonb/uuid columns.
        for (ColumnEntry col : table.columns()) {
            String name = col.name();
            if (name.equals("id")) continue;
            if (bind.containsKey(name)) continue;
            if (ignored.contains(name)) continue;
            if (isGeneratedColumn(col)) continue;
            if (!row.containsKey(name)) continue;
            Object val = row.get(name);
            if (val == null) continue;
            bind.put(name, new BoundValue(val, col.type()));
        }
        return true;
    }

    private Integer tryResolveViaLookup(TableEntry table, String fkColumn, Map<String, Object> row) {
        if (table.lookups() == null) return null;
        for (Lookup lk : table.lookups()) {
            if (!lk.via().equals(fkColumn)) continue;
            Object pickedValue = row.get(lk.emitAs());
            if (pickedValue == null) continue;
            ForeignKey fk = findFk(table, lk.via());
            if (fk == null) continue;
            Integer resolved = resolveByColumn(fk.refTable(), lk.pick(), pickedValue);
            if (resolved != null) return resolved;
        }
        return null;
    }

    /**
     * Holds a value with its declared PG type so the binder can pick the right converter/cast.
     */
    private record BoundValue(Object value, String type) {}

    /**
     * Tracks source-table-id → target-table-id mappings keyed by table name. The exporter never
     * leaks source ids except for the {@code id} column of each table; importer records the
     * (source-id, RETURNING-id) pair so downstream FK columns can be remapped.
     */
    public static final class IdRemapper {
        private final Map<String, Map<Integer, Integer>> maps = new LinkedHashMap<>();

        public void put(String table, int sourceId, int targetId) {
            maps.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(sourceId, targetId);
        }

        public Integer get(String table, int sourceId) {
            var m = maps.get(table);
            return m == null ? null : m.get(sourceId);
        }

        public Optional<Integer> find(String table, int sourceId) {
            return Optional.ofNullable(get(table, sourceId));
        }
    }
}
