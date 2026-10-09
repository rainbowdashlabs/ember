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
import java.util.HashMap;
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
 *   <li>A row whose foreign key names a row that has not arrived waits in {@link WaitingRows} until
 *       it does, and is left behind only when the run ends without it.</li>
 * </ul>
 */
public final class GenericTableImporter {
    private static final Logger log = LoggerFactory.getLogger(GenericTableImporter.class);

    private final DataTracking tracking;

    public GenericTableImporter(DataTracking tracking) {
        this.tracking = tracking;
    }

    /**
     * Looks up an id in {@code table} where {@code column = value}, among the rows the import may name
     * ({@link ImportReach}). Returns null when no such row matches. Adds a {@code ::uuid} cast to the
     * bound value whenever the target column type is declared as {@code uuid} in the tracking config -
     * Postgres rejects a {@code uuid = text} comparison without the cast.
     */
    private @Nullable Integer resolveByColumn(
            String table, String column, Object value, int stationId, IdRemapper idMap) {
        String columnType = lookupColumnType(table, column);
        String cast = "uuid".equalsIgnoreCase(columnType) ? "::uuid" : "";
        Integer found = query("SELECT t.id FROM " + table + " t WHERE t." + column + " = :v" + cast
                        + ImportReach.condition(table) + " LIMIT 1;")
                .single(ImportReach.bind(table, call().bind("v", value.toString()), stationId))
                .map(row -> row.getInt("id"))
                .first()
                .orElse(null);
        if (found == null || !ImportReach.mayName(table, found, stationId, idMap)) return null;
        return found;
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

    static @Nullable Integer toInteger(@Nullable Object val) {
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
     * Imports the given rows for {@code tableName} as a run of its own, giving up at the end of the
     * call whatever row names a row that did not arrive.
     *
     * @return number of rows written (rows whose FK remap could not be resolved are not)
     * @see #importRows(int, String, List, IdRemapper, WaitingRows)
     */
    public int importRows(int stationId, String tableName, List<Map<String, Object>> rows, IdRemapper idMap) {
        var waiting = new WaitingRows();
        int imported = importRows(stationId, tableName, rows, idMap, waiting);
        return imported + settle(stationId, idMap, waiting);
    }

    /**
     * Imports the given rows for {@code tableName} as part of a longer run.
     *
     * <p>A row whose foreign key names a row that has not arrived is not written, since it would point
     * at whatever row holds that id here. It waits in {@code waiting} instead, and
     * {@link #admitWaiting(int, IdRemapper, WaitingRows)} writes it once that row arrives. The references
     * that arrive empty and the array elements that are left out are counted and named in the log once
     * per call, so a reference the tracking file cannot follow shows up rather than costing rows unseen.
     *
     * @param waiting the rows of the run still waiting for a row they name
     * @return number of rows written now
     */
    public int importRows(
            int stationId, String tableName, List<Map<String, Object>> rows, IdRemapper idMap, WaitingRows waiting) {
        var losses = new ImportLosses();
        int imported = 0;
        for (Map<String, Object> row : rows) {
            if (write(stationId, tableName, row, idMap, waiting, losses, NOTHING_EMPTIES)) imported++;
        }
        losses.report();
        return imported;
    }

    /**
     * Writes the waiting rows whose missing row has arrived since, and those that waited for them in
     * turn.
     *
     * @param waiting the rows of the run still waiting for a row they name
     * @return number of rows written
     */
    public int admitWaiting(int stationId, IdRemapper idMap, WaitingRows waiting) {
        var losses = new ImportLosses();
        int written = retry(stationId, idMap, waiting, losses, NOTHING_EMPTIES);
        losses.report();
        return written;
    }

    /**
     * Ends a run. Rows still waiting are written where the only references they cannot follow are
     * optional ones, which then arrive empty; everything else still waiting is left behind and named
     * in the log.
     *
     * <p>An optional reference empties first where the row it names is not waiting itself, since a
     * waiting row may still be written and would then be named after all. Only once nothing moves any
     * more does one row at a time give up a reference to a row that is still waiting, and the rest are
     * tried again around it.
     *
     * @param waiting the rows of the run still waiting for a row they name
     * @return number of rows written
     */
    public int settle(int stationId, IdRemapper idMap, WaitingRows waiting) {
        var losses = new ImportLosses();
        EmptyReferences notOnTheirWay = (table, id) -> !waiting.holds(table, id);
        int written = retry(stationId, idMap, waiting, losses, NOTHING_EMPTIES);
        while (true) {
            written += retry(stationId, idMap, waiting, losses, notOnTheirWay);
            if (!writeFirstWritable(stationId, idMap, waiting, losses)) break;
            written++;
        }
        losses.report();
        waiting.countByReference()
                .forEach((reference, count) ->
                        log.warn("{}: {} row(s) not imported, their row did not arrive", reference, count));
        waiting.clear();
        return written;
    }

    /**
     * Tries the waiting rows again until a pass writes nothing more. While nothing may arrive empty,
     * only the rows whose missing row has arrived since are tried.
     */
    private int retry(
            int stationId, IdRemapper idMap, WaitingRows waiting, ImportLosses losses, EmptyReferences empties) {
        int written = 0;
        boolean progress = true;
        while (progress) {
            progress = false;
            var candidates = empties == NOTHING_EMPTIES ? waiting.unblocked(idMap) : waiting.all();
            for (var held : candidates) {
                waiting.release(held);
                if (write(stationId, held.table(), held.row(), idMap, waiting, losses, empties)) {
                    written++;
                    progress = true;
                }
            }
        }
        return written;
    }

    /**
     * Writes the first waiting row that can be written with every optional reference it cannot follow
     * left empty.
     *
     * @return {@code true} when a row was written
     */
    private boolean writeFirstWritable(int stationId, IdRemapper idMap, WaitingRows waiting, ImportLosses losses) {
        for (var held : waiting.all()) {
            waiting.release(held);
            if (write(stationId, held.table(), held.row(), idMap, waiting, losses, ANYTHING_EMPTIES)) return true;
        }
        return false;
    }

    /**
     * Writes one row, or holds it back in {@code waiting} when it names a row that has not arrived.
     *
     * @return {@code true} when the row was written
     */
    private boolean write(
            int stationId,
            String tableName,
            Map<String, Object> row,
            IdRemapper idMap,
            WaitingRows waiting,
            ImportLosses losses,
            EmptyReferences empties) {
        TableEntry table = tableEntry(tableName);
        Map<String, BoundValue> bind = new LinkedHashMap<>();
        var rowLosses = new RowLosses(tableName);
        ForeignKey unresolved = bindRow(table, row, stationId, idMap, bind, rowLosses, empties);
        if (unresolved != null) {
            waiting.hold(tableName, row, unresolved);
            return false;
        }
        losses.add(rowLosses);
        boolean hasIdPk = hasIntegerIdPk(table);
        String sql = buildInsertSql(tableName, bind, hasIdPk);
        if (hasIdPk) {
            Integer newId = executeInsertReturningId(sql, bind);
            Integer sourceId = toInteger(row.get("id"));
            if (newId != null && sourceId != null) idMap.put(tableName, sourceId, newId);
        } else {
            executeInsert(sql, bind);
        }
        return true;
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
     * <p>An optional reference whose row has not arrived arrives empty once {@code empties} allows it,
     * which is only at the end of a run and only where that row is not still waiting itself.
     *
     * @return the foreign key that could not be remapped, which holds the row back, or null when the
     * row is bound
     */
    private @Nullable ForeignKey bindRow(
            TableEntry table,
            Map<String, Object> row,
            int stationId,
            IdRemapper idMap,
            Map<String, BoundValue> bind,
            RowLosses losses,
            EmptyReferences empties) {
        ColumnEntry stationIdCol = findColumn(table, "station_id");
        if (stationIdCol != null) {
            bind.put("station_id", new BoundValue(stationId, "int4"));
        }

        Set<String> optional = Set.copyOf(table.stationTransfer().optionalReferences());
        Set<String> emptied = new HashSet<>();
        for (ForeignKey fk : table.foreignKeys()) {
            if ("station_id".equals(fk.column())) continue;
            Object sourceVal = row.get(fk.column());
            if (sourceVal == null) continue;
            Integer src = toInteger(sourceVal);
            if (src == null) continue;
            Integer mapped = idMap.get(fk.refTable(), src);
            if (mapped == null || mapped <= 0) {
                Integer viaLookup = tryResolveViaLookup(table, fk.column(), row, stationId, idMap);
                if (viaLookup != null) {
                    bind.put(fk.column(), new BoundValue(viaLookup, "int4"));
                    continue;
                }
                if (optional.contains(fk.column()) && empties.allowed(fk.refTable(), src)) {
                    emptied.add(fk.column());
                    losses.referenceEmpty(fk);
                    continue;
                }
                return fk;
            }
            bind.put(fk.column(), new BoundValue(mapped, "int4"));
        }

        ForeignKey unresolvedLookup = bindLookups(table, row, stationId, idMap, bind, emptied, losses);
        if (unresolvedLookup != null) return unresolvedLookup;

        Set<String> ignored = Set.copyOf(table.stationTransfer().ignoredColumns());
        for (ColumnEntry col : table.columns()) {
            String name = col.name();
            if (name.equals("id")) continue;
            if (bind.containsKey(name) || emptied.contains(name)) continue;
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
     * value to look for. A key the row's own foreign key already settled, bound or emptied, is left as
     * it is.
     *
     * @param emptied the optional references the row already leaves empty
     * @return the foreign key that leaves the row behind, or null when the row can be written
     */
    private @Nullable ForeignKey bindLookups(
            TableEntry table,
            Map<String, Object> row,
            int stationId,
            IdRemapper idMap,
            Map<String, BoundValue> bind,
            Set<String> emptied,
            RowLosses losses) {
        Map<String, ForeignKey> followed = new LinkedHashMap<>();
        Set<String> carried = new HashSet<>();
        for (Lookup lk : LookupSql.lookupsOf(table)) {
            if (emptied.contains(lk.via())) continue;
            ForeignKey fk = table.foreignKeyFor(lk.via());
            followed.put(lk.via(), fk);
            if (bind.containsKey(lk.via())) continue;
            Object pickedValue = row.get(lk.emitAs());
            if (pickedValue == null) continue;
            carried.add(lk.via());
            Integer resolvedId = resolveByColumn(fk.refTable(), lk.pick(), pickedValue, stationId, idMap);
            if (resolvedId != null) {
                bind.put(lk.via(), new BoundValue(resolvedId, "int4"));
            }
        }
        for (ForeignKey fk : followed.values()) {
            if (bind.containsKey(fk.column())) continue;
            ColumnEntry column = findColumn(table, fk.column());
            if (column != null && !column.nullable()) return fk;
            if (carried.contains(fk.column())) losses.referenceEmpty(fk);
        }
        return null;
    }

    /**
     * Moves the elements of every array that names rows of another table to the ids those rows got
     * here, leaving out and counting the elements whose row did not arrive.
     */
    private static void remapArrays(
            TableEntry table, IdRemapper idMap, Map<String, BoundValue> bind, RowLosses losses) {
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

    private @Nullable Integer tryResolveViaLookup(
            TableEntry table, String fkColumn, Map<String, Object> row, int stationId, IdRemapper idMap) {
        for (Lookup lk : LookupSql.lookupsOf(table)) {
            if (!lk.via().equals(fkColumn)) continue;
            Object pickedValue = row.get(lk.emitAs());
            if (pickedValue == null) continue;
            ForeignKey fk = table.foreignKeyFor(lk.via());
            Integer resolved = resolveByColumn(fk.refTable(), lk.pick(), pickedValue, stationId, idMap);
            if (resolved != null) return resolved;
        }
        return null;
    }

    /**
     * Holds a value with its declared PG type so the binder can pick the right converter/cast.
     */
    private record BoundValue(Object value, String type) {}

    /**
     * Whether an optional reference to a row that has not arrived may arrive empty.
     */
    @FunctionalInterface
    private interface EmptyReferences {
        boolean allowed(String refTable, int sourceId);
    }

    /** While tables are still being read, no reference arrives empty: its row may still come. */
    private static final EmptyReferences NOTHING_EMPTIES = (refTable, sourceId) -> false;

    /** Once nothing else moves, any optional reference may arrive empty. */
    private static final EmptyReferences ANYTHING_EMPTIES = (refTable, sourceId) -> true;

    /**
     * What one row written loses on the way: references that arrive empty and array elements that are
     * left out. It only counts once the row is actually written, so a row tried several times while it
     * waits is not counted several times.
     */
    private static final class RowLosses {
        private final String table;
        private final List<String> emptyReferences = new ArrayList<>();
        private final List<String> elementsLeftOut = new ArrayList<>();

        RowLosses(String table) {
            this.table = table;
        }

        void referenceEmpty(ForeignKey fk) {
            emptyReferences.add(describe(fk.column(), fk.refTable()));
        }

        void elementLeftOut(ArrayReference reference) {
            elementsLeftOut.add(describe(reference.column(), reference.refTable()));
        }

        private String describe(String column, String refTable) {
            return table + ": " + column + " -> " + refTable;
        }
    }

    /**
     * What one call of the import could not carry over, counted per table and column and written to
     * the log once at its end.
     */
    private static final class ImportLosses {
        private final Map<String, Integer> emptyReferences = new LinkedHashMap<>();
        private final Map<String, Integer> elementsLeftOut = new LinkedHashMap<>();

        void add(RowLosses row) {
            row.emptyReferences.forEach(reference -> emptyReferences.merge(reference, 1, Integer::sum));
            row.elementsLeftOut.forEach(reference -> elementsLeftOut.merge(reference, 1, Integer::sum));
        }

        void report() {
            emptyReferences.forEach((reference, count) ->
                    log.warn("{}: {} row(s) arrive without it, no such row was found here", reference, count));
            elementsLeftOut.forEach((reference, count) ->
                    log.warn("{}: {} element(s) left out, their row did not arrive", reference, count));
        }
    }

    /**
     * Tracks source-table-id → target-table-id mappings keyed by table name. The exporter never
     * leaks source ids except for the {@code id} column of each table; importer records the
     * (source-id, RETURNING-id) pair so downstream FK columns can be remapped.
     *
     * <p>It also knows which rows here arrived with the run, including those of a table whose ids do
     * not travel, such as an account found again by its address: a lookup may name them where it may
     * not name just any row ({@link ImportReach}).
     */
    public static final class IdRemapper {
        private final Map<String, Map<Integer, Integer>> maps = new LinkedHashMap<>();
        private final Map<String, Set<Integer>> arrived = new LinkedHashMap<>();

        public void put(String table, int sourceId, int targetId) {
            maps.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(sourceId, targetId);
            markArrived(table, targetId);
        }

        /**
         * Records that a row here arrived with the run, without a source id to map from.
         *
         * @param table    the table
         * @param targetId the row's id here
         */
        public void markArrived(String table, int targetId) {
            arrived.computeIfAbsent(table, k -> new HashSet<>()).add(targetId);
        }

        /**
         * @param table    the table
         * @param targetId a row's id here
         * @return whether that row arrived with the run
         */
        public boolean arrived(String table, int targetId) {
            return arrived.getOrDefault(table, Set.of()).contains(targetId);
        }

        public @Nullable Integer get(String table, int sourceId) {
            var m = maps.get(table);
            return m == null ? null : m.get(sourceId);
        }

        public Optional<Integer> find(String table, int sourceId) {
            return Optional.ofNullable(get(table, sourceId));
        }

        /**
         * The source id of every row of a table that arrived with one, by its id here.
         *
         * @param table the table
         * @return each row's source id, keyed by the row's id here
         */
        public Map<Integer, Integer> sourceIds(String table) {
            var inverse = new HashMap<Integer, Integer>();
            maps.getOrDefault(table, Map.of()).forEach((sourceId, targetId) -> inverse.put(targetId, sourceId));
            return inverse;
        }
    }
}
