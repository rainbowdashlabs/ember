/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.tracking.ArrayReference;
import dev.chojo.ember.tracking.ColumnEntry;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.TableEntry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *   <li>The elements of an {@link ArrayReference} column are remapped like a foreign key.</li>
 *   <li>Other non-ignored columns are bound as-is, with {@code jsonb}/{@code uuid}/
 *       {@code timestamptz}/{@code bytea}/array bindings inferred from {@link ColumnEntry#type()}.</li>
 *   <li>Tables with an {@code id} integer PK use {@code RETURNING id} so the
 *       source → target mapping is tracked for downstream tables. Tables without one (junction
 *       tables) insert with {@code ON CONFLICT DO NOTHING}.</li>
 * </ul>
 */
public final class GenericTableImporter {
    private static final Logger log = LoggerFactory.getLogger(GenericTableImporter.class);

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

    private @Nullable String lookupColumnType(String tableName, String columnName) {
        var t = tracking.tables().get(tableName);
        if (t == null) return null;
        for (ColumnEntry col : t.columns()) {
            if (col.name().equals(columnName)) return col.type();
        }
        return null;
    }

    private static @Nullable ColumnEntry findColumn(TableEntry table, String column) {
        for (var c : table.columns()) if (column.equals(c.name())) return c;
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

    private static @Nullable Integer toInteger(Object val) {
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
        if (bind.isEmpty()) return "SELECT 1;";
        var cols = new StringBuilder();
        var vals = new StringBuilder();
        int position = 0;
        for (var e : bind.entrySet()) {
            if (position > 0) {
                cols.append(", ");
                vals.append(", ");
            }
            cols.append(e.getKey());
            vals.append(':')
                    .append(tokenFor(position++))
                    .append(castFor(e.getValue().type()));
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
        int position = 0;
        for (var value : bind.values()) {
            c = bindOne(c, tokenFor(position++), value);
        }
        return c;
    }

    /**
     * The parameter the value at a position of the insert is bound under. It is named by the
     * position rather than the column, since a column name may hold digits ({@code sha256}) and a
     * query parameter may hold letters and underscores only.
     */
    static String tokenFor(int position) {
        var token = new StringBuilder("value_");
        int rest = position;
        do {
            token.append((char) ('a' + rest % 26));
            rest /= 26;
        } while (rest > 0);
        return token.toString();
    }

    /**
     * Binds one non-null value by its column type. A uuid or jsonb value is bound as a string; its
     * cast lives in the SQL. An array is bound as an SQL array of its element type.
     */
    private static Call bindOne(Call c, String name, BoundValue bv) {
        Object val = bv.value();
        String type = bv.type();
        if (ArrayValues.isArray(type)) return ArrayValues.bind(c, name, val, type);
        return switch (type == null ? "" : type) {
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
            case "bool" -> {
                Optional<Boolean> b = asBool(val);
                yield b.isPresent() ? c.bind(name, b.get()) : c.bind(name, (Boolean) null);
            }
            default -> c.bind(name, val.toString());
        };
    }

    private static @Nullable Double toDouble(Object val) {
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

    private static @Nullable Instant asInstant(Object val) {
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

    private static byte @Nullable [] asBytes(Object val) {
        if (val instanceof byte[] b) return b;
        if (val instanceof String s) return Base64.getDecoder().decode(s);
        return null;
    }

    /**
     * A transferred value as a boolean, or empty when it is neither a boolean nor text, which is then
     * written as SQL {@code NULL}.
     */
    private static Optional<Boolean> asBool(Object val) {
        if (val instanceof Boolean b) return Optional.of(b);
        if (val instanceof String s) return Optional.of(Boolean.parseBoolean(s));
        return Optional.empty();
    }

    /**
     * Imports the given rows for {@code tableName}.
     *
     * <p>A row whose foreign key names a row that did not arrive is not imported, since it would point
     * at whatever row holds that id here. Such rows are counted and named in the log once per call,
     * together with the references that arrive empty and the array elements that are left out, so a
     * reference the tracking file cannot follow shows up rather than costing rows unseen.
     *
     * @return number of rows written (rows whose FK remap could not be resolved are not)
     */
    public int importRows(int stationId, String tableName, List<Map<String, Object>> rows, IdRemapper idMap) {
        TableEntry table = tableEntry(tableName);
        Set<String> ignored = Set.copyOf(table.stationTransfer().ignoredColumns());
        boolean hasIdPk = hasIntegerIdPk(table);
        var losses = new ImportLosses(tableName);

        int imported = 0;
        for (Map<String, Object> row : rows) {
            Integer sourceId = toInteger(row.get("id"));
            Map<String, BoundValue> bind = new LinkedHashMap<>();

            ForeignKey unresolved = bindRow(table, row, stationId, ignored, idMap, bind, losses);
            if (unresolved != null) {
                losses.rowLeftBehind(unresolved);
                continue;
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
        losses.report();
        return imported;
    }

    private TableEntry tableEntry(String tableName) {
        var t = tracking.tables().get(tableName);
        if (t == null) throw new IllegalArgumentException("Unknown table: " + tableName);
        return t;
    }

    /**
     * Populates {@code bind} for the given row.
     *
     * <p>The station id comes from the import, not the row. Foreign keys are remapped through
     * {@code idMap}, falling back to a lookup-emitted value (such as an account's email) before the
     * row is given up. A foreign key whose own column is ignored is then resolved from the lookup
     * field that carries its key. The elements of an array that names rows of another table are moved
     * to the ids those rows got here. Everything else is copied as it stands.
     *
     * <p>A null source value leaves its column out of the insert, so the database fills in null or
     * the column default. Binding a typed null through JDBC would arrive as varchar, which the
     * database rejects against integer, jsonb and uuid columns.
     *
     * @return the foreign key that could not be remapped, which leaves the row behind, or null when the
     * row is bound
     */
    private @Nullable ForeignKey bindRow(
            TableEntry table,
            Map<String, Object> row,
            int stationId,
            Set<String> ignored,
            IdRemapper idMap,
            Map<String, BoundValue> bind,
            ImportLosses losses) {
        ColumnEntry stationIdCol = findColumn(table, "station_id");
        if (stationIdCol != null) {
            bind.put("station_id", new BoundValue(stationId, "int4"));
        }

        for (ForeignKey fk : table.foreignKeys()) {
            if ("station_id".equals(fk.column())) continue;
            Object sourceVal = row.get(fk.column());
            if (sourceVal == null) continue;
            Integer src = toInteger(sourceVal);
            if (src == null) continue;
            Integer mapped = idMap.get(fk.refTable(), src);
            if (mapped == null || mapped <= 0) {
                Integer viaLookup = tryResolveViaLookup(table, fk.column(), row);
                if (viaLookup != null) {
                    bind.put(fk.column(), new BoundValue(viaLookup, "int4"));
                    continue;
                }
                return fk;
            }
            bind.put(fk.column(), new BoundValue(mapped, "int4"));
        }

        ForeignKey unresolvedLookup = bindLookups(table, row, bind, losses);
        if (unresolvedLookup != null) return unresolvedLookup;

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
        remapArrays(table, idMap, bind, losses);
        return null;
    }

    /**
     * Resolves every foreign key the row carries by a lookup rather than by its id, such as an account
     * by its email or uid. A key none of whose lookups finds a row here leaves the row behind where the
     * column cannot be empty, and otherwise arrives empty, which is counted where the row carried a
     * value to look for.
     *
     * @return the foreign key that leaves the row behind, or null when the row can be written
     */
    private @Nullable ForeignKey bindLookups(
            TableEntry table, Map<String, Object> row, Map<String, BoundValue> bind, ImportLosses losses) {
        Map<String, ForeignKey> followed = new LinkedHashMap<>();
        Set<String> carried = new HashSet<>();
        for (Lookup lk : LookupSql.lookupsOf(table)) {
            ForeignKey fk = table.foreignKeyFor(lk.via());
            followed.put(lk.via(), fk);
            if (bind.containsKey(lk.via())) continue;
            Object pickedValue = row.get(lk.emitAs());
            if (pickedValue == null) continue;
            carried.add(lk.via());
            Integer resolvedId = resolveByColumn(fk.refTable(), lk.pick(), pickedValue);
            if (resolvedId != null) {
                bind.put(lk.via(), new BoundValue(resolvedId, "int4"));
            }
        }
        for (ForeignKey fk : followed.values()) {
            if (bind.containsKey(fk.column())) continue;
            ColumnEntry column = findColumn(table, fk.column());
            if (column != null && !column.nullable()) return fk;
            if (carried.contains(fk.column())) losses.lookupUnresolved(fk);
        }
        return null;
    }

    /**
     * Moves the elements of every array that names rows of another table to the ids those rows got
     * here, leaving out and counting the elements whose row did not arrive.
     */
    private static void remapArrays(
            TableEntry table, IdRemapper idMap, Map<String, BoundValue> bind, ImportLosses losses) {
        for (ArrayReference reference :
                Objects.requireNonNullElse(table.arrayReferences(), List.<ArrayReference>of())) {
            BoundValue bound = bind.get(reference.column());
            if (bound == null) continue;
            List<Integer> mapped = new ArrayList<>();
            for (Object element : ArrayValues.elements(bound.value())) {
                Integer source = element == null ? null : toInteger(element);
                Integer target = source == null ? null : idMap.get(reference.refTable(), source);
                if (target == null) {
                    losses.elementLeftOut(reference);
                } else {
                    mapped.add(target);
                }
            }
            bind.put(reference.column(), new BoundValue(mapped, bound.type()));
        }
    }

    private @Nullable Integer tryResolveViaLookup(TableEntry table, String fkColumn, Map<String, Object> row) {
        for (Lookup lk : LookupSql.lookupsOf(table)) {
            if (!lk.via().equals(fkColumn)) continue;
            Object pickedValue = row.get(lk.emitAs());
            if (pickedValue == null) continue;
            ForeignKey fk = table.foreignKeyFor(lk.via());
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
     * What one call of the import could not carry over, counted per column and written to the log
     * once at its end.
     */
    private static final class ImportLosses {
        private final String table;
        private final Map<String, Integer> rowsLeftBehind = new LinkedHashMap<>();
        private final Map<String, Integer> emptyReferences = new LinkedHashMap<>();
        private final Map<String, Integer> elementsLeftOut = new LinkedHashMap<>();

        ImportLosses(String table) {
            this.table = table;
        }

        void rowLeftBehind(ForeignKey fk) {
            rowsLeftBehind.merge(describe(fk.column(), fk.refTable()), 1, Integer::sum);
        }

        void lookupUnresolved(ForeignKey fk) {
            emptyReferences.merge(describe(fk.column(), fk.refTable()), 1, Integer::sum);
        }

        void elementLeftOut(ArrayReference reference) {
            elementsLeftOut.merge(describe(reference.column(), reference.refTable()), 1, Integer::sum);
        }

        void report() {
            rowsLeftBehind.forEach((reference, count) ->
                    log.warn("{}: {} row(s) not imported, their {} row did not arrive", table, count, reference));
            emptyReferences.forEach((reference, count) -> log.warn(
                    "{}: {} row(s) arrive without their {}, no such row was found here", table, count, reference));
            elementsLeftOut.forEach((reference, count) ->
                    log.warn("{}: {} element(s) left out, their {} row did not arrive", table, count, reference));
        }

        private static String describe(String column, String refTable) {
            return column + " -> " + refTable;
        }
    }

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

        public @Nullable Integer get(String table, int sourceId) {
            var m = maps.get(table);
            return m == null ? null : m.get(sourceId);
        }

        public Optional<Integer> find(String table, int sourceId) {
            return Optional.ofNullable(get(table, sourceId));
        }
    }
}
