/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The migrations a development database was built from, and the schema thrown away when they change. */
class SchemaFingerprintTest extends RepositoryTestBase {
    private static final String PROBE = "fingerprint_probe";

    @TempDir
    Path dir;

    private static boolean schemaExists() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)")) {
            statement.setString(1, PROBE);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static void createProbe() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + PROBE);
        }
    }

    @Test
    void aDatabaseWithNothingRecordedCountsAsChanged() {
        assertTrue(new SchemaFingerprint(dir.resolve("hash")).changed());
    }

    @Test
    void recordingTheMigrationsMakesThemUnchangedUntilTheRecordDiffers() throws Exception {
        var file = dir.resolve("hash");
        var fingerprint = new SchemaFingerprint(file);

        fingerprint.record();
        assertFalse(fingerprint.changed());

        Files.writeString(file, "built from another branch");
        assertTrue(fingerprint.changed());
    }

    @Test
    void aChangedFingerprintDropsTheSchemaAndAnUnchangedOneLeavesIt() throws Exception {
        var fingerprint = new SchemaFingerprint(dir.resolve("hash"));
        fingerprint.record();
        createProbe();

        fingerprint.dropSchemaIfChanged(dataSource, PROBE);
        assertTrue(schemaExists());

        Files.writeString(dir.resolve("hash"), "built from another branch");
        fingerprint.dropSchemaIfChanged(dataSource, PROBE);
        assertFalse(schemaExists());
    }
}
