/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Writes the profile field tables to the data volume before they are merged.
 *
 * <p>Patch 1.60 turns a field that carried its audience into a definition with assignments, and where
 * a station asked the same question of two kinds of member the two rows become one. Almost all of
 * that is recoverable afterwards: the audiences survive as assignments carrying their position, width
 * and readonly. An answer that loses to another does not, and neither does a definition dropped as a
 * duplicate.
 *
 * <p>So a copy is taken, and it is taken here rather than in SQL. A copy left in a table beside the
 * live ones is exported with a station and imported into the next one; a copy in a schema of its own
 * needs a right on the database that a role confined to one schema does not have, and an instance
 * without it would fail half way through the patch with no way forward. The data volume is neither.
 *
 * <p>Nothing reads this back automatically. It is there for somebody who has to answer what a member
 * used to have, and it can be deleted once an upgrade has been looked at.
 */
public final class ProfileFieldMergeBackup {
    private static final Logger log = LoggerFactory.getLogger(ProfileFieldMergeBackup.class);

    private static final Path DIR = Path.of("data", "migrations");

    private static final String[] TABLES = {
        "profile_field", "profile_field_value", "cluster_profile_field", "cluster_profile_field_value"
    };

    private ProfileFieldMergeBackup() {}

    /**
     * Copies the four tables to {@code data/migrations} as one JSON document.
     *
     * <p>A failure here is logged and swallowed on purpose. The copy is a convenience for whoever has
     * to answer a question afterwards, and a volume that is full or read-only is not a reason to stop
     * an instance upgrading and leave it on a schema its code no longer matches.
     *
     * @param connection the connection the updater is applying the patch on
     * @param schema     the schema being upgraded
     */
    public static void writeTo(Connection connection, String schema) {
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve("profile-field-merge-" + Instant.now().toEpochMilli() + ".json");
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write("{\n");
                for (int i = 0; i < TABLES.length; i++) {
                    writer.write("  \"" + TABLES[i] + "\": ");
                    writeTable(connection, schema, TABLES[i], writer);
                    writer.write(i == TABLES.length - 1 ? "\n" : ",\n");
                }
                writer.write("}\n");
            }
            log.info("Profile fields copied to {} before they are merged", file);
        } catch (IOException | SQLException e) {
            log.warn(
                    "Could not copy the profile fields before merging them. The upgrade goes ahead; what it"
                            + " discards will only be in the log.",
                    e);
        }
    }

    /**
     * One table as a JSON array, built by the database so no column has to be named here.
     *
     * <p>{@code json_agg} of the whole row keeps this honest across the four tables and across the
     * shapes they have had over time: a column added by an older patch is copied because the row
     * carries it, not because somebody remembered to add it to a list.
     */
    private static void writeTable(Connection connection, String schema, String table, Writer writer)
            throws SQLException, IOException {
        String sql = "SELECT coalesce(json_agg(t), '[]')::TEXT FROM \"" + schema + "\".\"" + table + "\" t";
        try (var statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            writer.write(rows.next() ? rows.getString(1) : "[]");
        }
    }
}
