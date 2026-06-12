/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic SHA-256 hashing for schema components.
 * Hashes are stable across runs as long as the underlying schema doesn't change.
 */
public final class HashComputer {

    private HashComputer() {}

    /**
     * Hash of a single table — columns (sorted by name) + FKs (sorted by column).
     */
    public static String tableHash(SchemaReader.RawTable table) {
        var sb = new StringBuilder();

        table.columns.stream()
                .sorted(Comparator.comparing(SchemaReader.RawColumn::name))
                .forEach(c -> sb.append("col|")
                        .append(c.name())
                        .append('|')
                        .append(c.type())
                        .append('|')
                        .append(c.nullable() ? "null" : "notnull")
                        .append('\n'));

        table.foreignKeys.stream()
                .sorted(Comparator.comparing(SchemaReader.RawForeignKey::column))
                .forEach(fk -> sb.append("fk|")
                        .append(fk.column())
                        .append('|')
                        .append(fk.refTable())
                        .append('|')
                        .append(fk.refColumn())
                        .append('|')
                        .append(fk.deleteRule())
                        .append('\n'));

        return sha256("sha256:", sb.toString());
    }

    /**
     * Top-level schema hash combining all table hashes (sorted by table name).
     */
    public static String schemaHash(Map<String, TableEntry> tables) {
        var sorted = new TreeMap<>(tables);
        var sb = new StringBuilder();
        sorted.forEach((name, entry) ->
                sb.append(name).append('=').append(entry.tableHash()).append('\n'));
        return sha256("sha256:", sb.toString());
    }

    private static String sha256(String prefix, String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
