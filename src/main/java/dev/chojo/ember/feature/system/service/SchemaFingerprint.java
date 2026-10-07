/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.util.Sha256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HexFormat;

import javax.sql.DataSource;

/**
 * The migrations a development database was last built from, kept as a hash of the schema version
 * and every patch beside the working directory.
 *
 * <p>A development database follows whatever branch is checked out. When the migrations change, the
 * data is thrown away and seeded again, which {@link DemoService} does. A branch whose patches end
 * below the database's version cannot even start, though: the migrations refuse a database that is
 * ahead of them before anything else runs. So a changed fingerprint also drops the schema before the
 * migrations run ({@link #dropSchemaIfChanged}), and they build it again from nothing.
 *
 * <p>The file is suffixed with the {@code DB_HOST} env var so two backends running off the same
 * source tree (e.g. the {@code transfer} compose profile, which bind-mounts the project root into both
 * containers) keep separate fingerprints instead of racing on a single shared file. {@code DB_HOST} is
 * preferred over {@code HOSTNAME} because the container hostname defaults to a random per-run docker
 * container id and would orphan a fresh sentinel on every {@code compose up}; the configured database
 * host is stable across restarts and unique per stack by construction.
 */
public final class SchemaFingerprint {
    private static final Logger log = LoggerFactory.getLogger(SchemaFingerprint.class);

    private final Path file;

    /**
     * @param file where the fingerprint of the last build is kept
     */
    public SchemaFingerprint(Path file) {
        this.file = file;
    }

    /**
     * The fingerprint of the database this process talks to.
     *
     * @return the fingerprint kept beside the working directory
     */
    public static SchemaFingerprint ofThisDatabase() {
        String key = System.getenv("DB_HOST");
        if (key == null || key.isBlank()) return new SchemaFingerprint(Path.of(".demo-schema-hash"));
        return new SchemaFingerprint(Path.of(".demo-schema-hash." + key.replaceAll("[^A-Za-z0-9._-]", "_")));
    }

    /**
     * Whether the migrations differ from the ones the database was last built from.
     *
     * @return true where they differ, nothing was recorded yet, or the record cannot be read
     */
    public boolean changed() {
        try {
            if (!Files.exists(file)) return true;
            return !Files.readString(file).strip().equals(compute());
        } catch (IOException e) {
            log.warn("Could not read schema hash, will re-seed", e);
            return true;
        }
    }

    /** Records the current migrations as the ones the database was built from. */
    public void record() {
        try {
            Files.writeString(file, compute());
        } catch (IOException e) {
            log.warn("Could not write schema hash file", e);
        }
    }

    /**
     * Drops the schema of a development database built from other migrations, so the migrations that
     * run next build it from nothing, also where it is ahead of them.
     *
     * @param source the database
     * @param schema the schema the application keeps its tables in
     * @throws SQLException where the schema cannot be dropped
     */
    public void dropSchemaIfChanged(DataSource source, String schema) throws SQLException {
        if (!changed()) return;
        log.info("Dev mode: schema changed, dropping schema {} before migrating it", schema);
        try (var connection = source.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + schema.replace("\"", "\"\"") + "\" CASCADE");
        }
    }

    String compute() {
        try {
            var digest = Sha256.digest();
            try (InputStream is = SchemaFingerprint.class.getResourceAsStream("/database/version")) {
                if (is != null) digest.update(is.readAllBytes());
            }
            for (int i = 1; ; i++) {
                try (InputStream is =
                        SchemaFingerprint.class.getResourceAsStream("/database/postgresql/1/patch_" + i + ".sql")) {
                    if (is == null) break;
                    digest.update(is.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute schema hash", e);
        }
    }
}
