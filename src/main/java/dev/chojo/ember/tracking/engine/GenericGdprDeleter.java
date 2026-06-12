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
import dev.chojo.ember.tracking.DeletionStrategy;
import dev.chojo.ember.tracking.IdentityColumn;
import dev.chojo.ember.tracking.IdentityType;
import dev.chojo.ember.tracking.Status;
import dev.chojo.ember.tracking.Strategy;
import dev.chojo.ember.tracking.TableEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Applies GDPR deletion strategies declared in {@code data_tracking.json}.
 *
 * <p>For each TRACKED {@code gdprDeletion} entry, the engine inspects each strategy whose column
 * matches the requested {@link IdentityType} and runs the corresponding operation:
 * <ul>
 *   <li>{@link Strategy#DELETE_EXPLICIT} — {@code DELETE FROM table WHERE col = :id}</li>
 *   <li>{@link Strategy#NULL}             — {@code UPDATE table SET col = NULL WHERE col = :id}
 *                                          (requires the column to be nullable)</li>
 *   <li>{@link Strategy#ANONYMIZE}        — {@code UPDATE table SET col = <sentinel> WHERE col = :id}
 *                                          where the sentinel is type-derived: zero-UUID for {@code uuid},
 *                                          the localised "Gelöscht" string for {@code text}, NULL for a
 *                                          nullable int.</li>
 *   <li>{@link Strategy#CASCADE}          — no-op; the FK CASCADE handles it. Note: this only fires
 *                                          when the parent row is actually deleted, see the warnings
 *                                          surfaced in the admin panel.</li>
 *   <li>{@link Strategy#RETAIN}, {@link Strategy#RETAIN_UNLINKED}, {@link Strategy#NOT_APPLICABLE}
 *                                       — no-op; the row is preserved by design.</li>
 * </ul>
 *
 * <p>UPDATEs run first across all tables, then DELETEs in reverse topological order so children
 * are removed before parents and FK constraints aren't violated.
 */
public final class GenericGdprDeleter {

    private static final Logger log = LoggerFactory.getLogger(GenericGdprDeleter.class);
    public static final String ANONYMIZE_TEXT = "Gelöscht";
    public static final UUID ANONYMIZE_UUID = new UUID(0L, 0L);

    private final DataTracking tracking;
    private final List<String> deletionOrder;

    public GenericGdprDeleter(DataTracking tracking) {
        this.tracking = tracking;
        var topo = new ArrayList<>(TableOrder.topological(tracking));
        Collections.reverse(topo);
        this.deletionOrder = List.copyOf(topo);
    }

    /**
     * Applies every TRACKED deletion strategy whose identity column matches {@code type}.
     * Returns a structured report of what changed.
     */
    public Report deleteByIdentity(IdentityType type, Object identityValue) {
        var report = new Report();

        // Phase 1 — UPDATE operations (NULL + ANONYMIZE). Must run before the DELETEs so we don't
        // lose the rows we'd anonymise via cascade.
        for (String tableName : deletionOrder) {
            applyUpdatesForTable(tableName, type, identityValue, report);
        }

        // Phase 2 — DELETE operations, children-first.
        for (String tableName : deletionOrder) {
            applyDeletesForTable(tableName, type, identityValue, report);
        }
        return report;
    }

    // -- per-table operations ------------------------------------------------

    private void applyUpdatesForTable(String tableName, IdentityType type, Object idVal, Report report) {
        var table = tracking.tables() == null ? null : tracking.tables().get(tableName);
        if (table == null || table.gdprDeletion() == null) return;
        if (table.gdprDeletion().status() != Status.TRACKED) return;
        var strategies = table.gdprDeletion().strategies();
        if (strategies == null) return;

        for (DeletionStrategy s : strategies) {
            ColumnEntry col = resolveColumn(table, s.column());
            if (col == null) {
                report.skipped.add(new SkippedOp(tableName, s.column(), s.strategy(), "column not found on table"));
                continue;
            }
            if (!identityMatchesColumn(table, type, col.name())) continue;

            switch (s.strategy()) {
                case NULL -> runNullUpdate(tableName, col, type, idVal, report);
                case ANONYMIZE -> runAnonymizeUpdate(tableName, col, type, idVal, report);
                case CASCADE, RETAIN, RETAIN_UNLINKED, NOT_APPLICABLE ->
                    report.noOps.add(new NoOp(tableName, s.column(), s.strategy()));
                case DELETE_EXPLICIT -> {
                    /* handled in phase 2 */
                }
            }
        }
    }

    private void applyDeletesForTable(String tableName, IdentityType type, Object idVal, Report report) {
        var table = tracking.tables() == null ? null : tracking.tables().get(tableName);
        if (table == null || table.gdprDeletion() == null) return;
        if (table.gdprDeletion().status() != Status.TRACKED) return;
        var strategies = table.gdprDeletion().strategies();
        if (strategies == null) return;

        for (DeletionStrategy s : strategies) {
            if (s.strategy() != Strategy.DELETE_EXPLICIT) continue;
            ColumnEntry col = resolveColumn(table, s.column());
            if (col == null) {
                report.skipped.add(new SkippedOp(tableName, s.column(), s.strategy(), "column not found on table"));
                continue;
            }
            if (!identityMatchesColumn(table, type, col.name())) continue;
            runDelete(tableName, col, type, idVal, report);
        }
    }

    // -- SQL emitters --------------------------------------------------------

    private void runDelete(String tableName, ColumnEntry col, IdentityType type, Object idVal, Report report) {
        String cast = type == IdentityType.MEMBER_UID ? "::uuid" : "";
        String sql = "DELETE FROM " + tableName + " WHERE " + col.name() + " = :id" + cast + ";";
        int rows = query(sql).single(bindIdentity(type, idVal)).delete().rows();
        report.executed.add(new ExecutedOp(tableName, col.name(), Strategy.DELETE_EXPLICIT, rows));
    }

    private void runNullUpdate(String tableName, ColumnEntry col, IdentityType type, Object idVal, Report report) {
        if (!col.nullable()) {
            report.skipped.add(new SkippedOp(tableName, col.name(), Strategy.NULL, "column is NOT NULL"));
            return;
        }
        String cast = type == IdentityType.MEMBER_UID ? "::uuid" : "";
        String sql =
                "UPDATE " + tableName + " SET " + col.name() + " = NULL WHERE " + col.name() + " = :id" + cast + ";";
        int rows = query(sql).single(bindIdentity(type, idVal)).update().rows();
        report.executed.add(new ExecutedOp(tableName, col.name(), Strategy.NULL, rows));
    }

    private void runAnonymizeUpdate(String tableName, ColumnEntry col, IdentityType type, Object idVal, Report report) {
        // Pick a sentinel by column type. Anonymising means the row is preserved but the identity
        // value is replaced with a non-identifying placeholder.
        String castWhere = type == IdentityType.MEMBER_UID ? "::uuid" : "";
        Call c;
        String sql;
        switch (col.type()) {
            case "uuid" -> {
                sql = "UPDATE " + tableName + " SET " + col.name() + " = :anon::uuid WHERE " + col.name() + " = :id"
                        + castWhere + ";";
                c = bindIdentity(type, idVal).bind("anon", ANONYMIZE_UUID, StandardValueConverter.UUID_STRING);
            }
            case "text", "varchar", "char" -> {
                sql = "UPDATE " + tableName + " SET " + col.name() + " = :anon WHERE " + col.name() + " = :id"
                        + castWhere + ";";
                c = bindIdentity(type, idVal).bind("anon", ANONYMIZE_TEXT);
            }
            case "int4", "int8" -> {
                if (col.nullable()) {
                    // No safe integer sentinel — fall back to NULL.
                    sql = "UPDATE " + tableName + " SET " + col.name() + " = NULL WHERE " + col.name() + " = :id"
                            + castWhere + ";";
                    c = bindIdentity(type, idVal);
                } else {
                    report.skipped.add(new SkippedOp(
                            tableName,
                            col.name(),
                            Strategy.ANONYMIZE,
                            "NOT NULL integer column needs a placeholder member/account — manual handling required"));
                    return;
                }
            }
            default -> {
                report.skipped.add(new SkippedOp(
                        tableName,
                        col.name(),
                        Strategy.ANONYMIZE,
                        "no anonymisation sentinel known for column type " + col.type()));
                return;
            }
        }
        int rows = query(sql).single(c).update().rows();
        report.executed.add(new ExecutedOp(tableName, col.name(), Strategy.ANONYMIZE, rows));
    }

    // -- helpers -------------------------------------------------------------

    private static ColumnEntry resolveColumn(TableEntry table, String name) {
        if (table.columns() == null) return null;
        for (var c : table.columns()) if (c.name().equals(name)) return c;
        return null;
    }

    /**
     * Returns true when {@code column} is listed as an identity column of the requested type. Without
     * this check we'd run a DELETE/UPDATE on a strategy that isn't related to the requested identity —
     * e.g. station_member.id has DELETE_EXPLICIT but only for MEMBER_ID identity, never for ACCOUNT_ID.
     */
    private static boolean identityMatchesColumn(TableEntry table, IdentityType type, String column) {
        var ctx = table.gdprExport();
        if (ctx == null || ctx.identityColumns() == null) return true; // permissive when no identity declared
        for (IdentityColumn ic : ctx.identityColumns()) {
            if (ic.type() == type && column.equals(ic.column())) return true;
        }
        return false;
    }

    private static Call bindIdentity(IdentityType type, Object idVal) {
        Call c = call();
        return switch (type) {
            case ACCOUNT_ID, MEMBER_ID -> c.bind("id", asInt(idVal));
            case MEMBER_UID -> c.bind("id", asUuid(idVal), StandardValueConverter.UUID_STRING);
        };
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }

    private static UUID asUuid(Object o) {
        if (o instanceof UUID u) return u;
        return UUID.fromString(o.toString());
    }

    // -- report types --------------------------------------------------------

    public record ExecutedOp(String table, String column, Strategy strategy, int rowsAffected) {}

    public record SkippedOp(String table, String column, Strategy strategy, String reason) {}

    public record NoOp(String table, String column, Strategy strategy) {}

    /**
     * Structured report of one {@link #deleteByIdentity} call. Use {@link #toSummary()} to produce a
     * compact log-friendly view.
     */
    public static final class Report {
        public final List<ExecutedOp> executed = new ArrayList<>();
        public final List<SkippedOp> skipped = new ArrayList<>();
        public final List<NoOp> noOps = new ArrayList<>();

        public Map<String, Object> toSummary() {
            int totalRows = executed.stream().mapToInt(ExecutedOp::rowsAffected).sum();
            Map<Strategy, Integer> byStrategy = new LinkedHashMap<>();
            for (var op : executed) byStrategy.merge(op.strategy(), op.rowsAffected(), Integer::sum);
            return Map.of(
                    "executed", executed.size(),
                    "totalRowsAffected", totalRows,
                    "byStrategy", byStrategy,
                    "skipped", skipped,
                    "noOps", noOps.size());
        }

        public void log(Logger logger) {
            logger.info(
                    "GDPR deletion report — executed={}, rows={}, skipped={}, no-ops={}",
                    executed.size(),
                    executed.stream().mapToInt(ExecutedOp::rowsAffected).sum(),
                    skipped.size(),
                    noOps.size());
            for (var s : skipped) {
                logger.warn("  SKIPPED {} {}.{} — {}", s.strategy(), s.table(), s.column(), s.reason());
            }
        }
    }
}
