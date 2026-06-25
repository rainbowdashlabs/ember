/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

/**
 * Reads the live PostgreSQL schema via {@code information_schema} and exposes
 * it as a normalized structure for comparison with the tracking file.
 */
public final class SchemaReader {

    private final DataSource dataSource;
    private final String schema;

    public SchemaReader(DataSource dataSource, String schema) {
        this.dataSource = dataSource;
        this.schema = schema;
    }

    /**
     * Reads all tables in the configured schema (sorted by table name).
     * For each table, returns its columns and an optional list of FK definitions.
     */
    public Map<String, RawTable> readTables() throws SQLException {
        Map<String, RawTable> result = new TreeMap<>();

        // Tables + their COMMENT ON TABLE descriptions
        try (var conn = dataSource.getConnection();
                var stmt = conn.prepareStatement("""
                     SELECT t.table_name,
                            obj_description((quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass)
                                AS description
                     FROM information_schema.tables t
                     WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE'
                     ORDER BY t.table_name;
                     """)) {
            stmt.setString(1, schema);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    result.put(
                            tableName,
                            new RawTable(tableName, rs.getString("description"), new ArrayList<>(), new ArrayList<>()));
                }
            }
        }

        // Columns + their COMMENT ON COLUMN descriptions (via pg_description on pg_attribute)
        try (var conn = dataSource.getConnection();
                var stmt = conn.prepareStatement("""
                     SELECT c.table_name,
                            c.column_name,
                            c.udt_name,
                            c.is_nullable,
                            c.ordinal_position,
                            col_description(
                                (quote_ident(c.table_schema) || '.' || quote_ident(c.table_name))::regclass,
                                c.ordinal_position
                            ) AS description
                     FROM information_schema.columns c
                     WHERE c.table_schema = ?
                     ORDER BY c.table_name, c.ordinal_position;
                     """)) {
            stmt.setString(1, schema);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var table = result.get(rs.getString("table_name"));
                    if (table == null) continue;
                    table.columns.add(new RawColumn(
                            rs.getString("column_name"),
                            rs.getString("udt_name"),
                            "YES".equals(rs.getString("is_nullable")),
                            rs.getString("description")));
                }
            }
        }

        // Foreign keys with delete rule
        try (var conn = dataSource.getConnection();
                var stmt = conn.prepareStatement("""
                     SELECT tc.table_name,
                            kcu.column_name,
                            ccu.table_name  AS ref_table,
                            ccu.column_name AS ref_column,
                            rc.delete_rule
                     FROM information_schema.table_constraints tc
                     JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_name = kcu.constraint_name
                         AND tc.table_schema = kcu.table_schema
                     JOIN information_schema.referential_constraints rc
                          ON tc.constraint_name = rc.constraint_name
                         AND tc.table_schema = rc.constraint_schema
                     JOIN information_schema.constraint_column_usage ccu
                          ON rc.unique_constraint_name = ccu.constraint_name
                         AND tc.table_schema = ccu.table_schema
                     WHERE tc.constraint_type = 'FOREIGN KEY'
                       AND tc.table_schema = ?
                     ORDER BY tc.table_name, kcu.column_name;
                     """)) {
            stmt.setString(1, schema);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var table = result.get(rs.getString("table_name"));
                    if (table == null) continue;
                    table.foreignKeys.add(new RawForeignKey(
                            rs.getString("column_name"),
                            rs.getString("ref_table"),
                            rs.getString("ref_column"),
                            rs.getString("delete_rule")));
                }
            }
        }

        return result;
    }

    /**
     * Raw table view assembled from information_schema.
     */
    public static final class RawTable {
        public final String name;
        public final String description;
        public final List<RawColumn> columns;
        public final List<RawForeignKey> foreignKeys;

        RawTable(String name, String description, List<RawColumn> columns, List<RawForeignKey> foreignKeys) {
            this.name = name;
            this.description = description;
            this.columns = columns;
            this.foreignKeys = foreignKeys;
        }

        /**
         * Map of column name to RawColumn for quick lookup.
         */
        public Map<String, RawColumn> columnsByName() {
            var map = new LinkedHashMap<String, RawColumn>();
            for (var c : columns) map.put(c.name(), c);
            return map;
        }
    }

    /**
     * @param description {@code COMMENT ON COLUMN} text, or {@code null} when the column has no comment.
     *                    Excluded from the table hash so editing a comment does not invalidate verification.
     */
    public record RawColumn(String name, String type, boolean nullable, String description) {}

    /**
     * A foreign key, including its ON DELETE rule for cascade verification.
     */
    public record RawForeignKey(String column, String refTable, String refColumn, String deleteRule) {}
}
