/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Manages the demo environment: wipes and re-creates the schema, then runs every bound
 * {@link DemoSeeder} in ascending order band. Seeders inside one band run in parallel and the
 * run joins between bands, so a band only ever sees data produced by lower bands.
 */
@Singleton
public class DemoService {
    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    /**
     * Location of the schema-fingerprint sentinel used to decide whether the demo seeder can
     * skip re-running. Suffixed with the {@code DB_HOST} env var so two backends running off
     * the same source tree (e.g. the {@code transfer} compose profile, which bind-mounts the
     * project root into both containers) keep separate fingerprints instead of racing on a
     * single shared file. {@code DB_HOST} is preferred over {@code HOSTNAME} because the
     * container hostname defaults to a random per-run docker container id and would orphan a
     * fresh sentinel on every {@code compose up}; the configured database host is stable
     * across restarts and unique per stack by construction.
     */
    private static final Path SCHEMA_HASH_FILE = resolveSchemaHashFile();

    private final Demo demoConfig;
    private final Database databaseConfig;
    private final DataSource dataSource;
    private final PasswordHasher passwordHasher;
    private final Set<DemoSeeder> seeders;
    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;
    private final StorageBackendResolver backendResolver;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile Instant lastActivity = Instant.now();
    private volatile boolean needsReset = false;

    @Inject
    public DemoService(
            Demo demoConfig,
            Database databaseConfig,
            DataSource dataSource,
            PasswordHasher passwordHasher,
            Set<DemoSeeder> seeders,
            StationRepository stationRepository,
            ClusterRepository clusterRepository,
            StorageBackendResolver backendResolver) {
        this.demoConfig = demoConfig;
        this.databaseConfig = databaseConfig;
        this.dataSource = dataSource;
        this.passwordHasher = passwordHasher;
        this.seeders = seeders;
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
        this.backendResolver = backendResolver;
    }

    private static Path resolveSchemaHashFile() {
        String key = System.getenv("DB_HOST");
        if (key == null || key.isBlank()) {
            return Path.of(".demo-schema-hash");
        }
        return Path.of(".demo-schema-hash." + sanitizeForFilename(key));
    }

    private static String sanitizeForFilename(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public boolean isEnabled() {
        return demoConfig.enabled() || demoConfig.dev();
    }

    public void initialize() {
        if (demoConfig.dev()) {
            if (schemaUnchanged()) {
                log.info("Dev mode: schema unchanged, skipping seed.");
                return;
            }
            log.info("Dev mode: schema changed, re-seeding database...");
            if (!seedQuietly()) return;
            writeSchemaHash();
            return;
        }
        if (!demoConfig.enabled()) return;
        log.info("Demo mode enabled. Idle reset after {} minutes of inactivity", demoConfig.idleResetMinutes());
        seedQuietly();
        scheduler.scheduleAtFixedRate(this::checkIdleReset, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Records an authenticated request. Resets the idle timer and marks the data as dirty.
     * Called from the API access handler on every authenticated request.
     */
    public void recordActivity() {
        lastActivity = Instant.now();
        needsReset = true;
    }

    /**
     * Throws away the schema, migrates it back and seeds it again.
     *
     * <p>Failure is raised rather than logged, because a caller that goes on regardless works against
     * a database that is neither the old one nor a seeded one. The end-to-end suite asks for this
     * before every run and takes the answer as its guarantee that the data is fresh: swallowed here,
     * a failed wipe reads to it as a clean start and every story after it fails somewhere else.
     * Callers that must not fall over, the ones on the start up path, catch it themselves.
     */
    public void resetAndSeed() {
        log.info("Demo: Wiping and re-seeding database...");
        wipeDatabase();
        invalidateCachesOfTheDiscardedData();
        seedData();
        log.info("Demo: Database seeded successfully");
    }

    /**
     * Forgets what was remembered about the stations and associations just thrown away.
     *
     * <p>The identities are cached in memory, and so is every answer to "where does this station keep
     * its files". Station identifiers start again from the same numbers after a wipe, so a stale entry
     * hands the next station to hold a number the storage of the one that held it before, which reads
     * as a file vanishing on the first move somebody makes.
     */
    private void invalidateCachesOfTheDiscardedData() {
        stationRepository.invalidateIdentityCaches();
        clusterRepository.invalidateIdentityCache();
        backendResolver.invalidateAll();
    }

    private void checkIdleReset() {
        if (!needsReset) return;
        var idleMinutes = Duration.between(lastActivity, Instant.now()).toMinutes();
        if (idleMinutes >= demoConfig.idleResetMinutes()) {
            log.info("Demo: {} minutes idle, resetting data...", idleMinutes);
            needsReset = false;
            seedQuietly();
        }
    }

    /**
     * Seeds where nothing is waiting for an answer: the start up path and the idle timer.
     *
     * <p>Neither may fall over. An instance that cannot seed still has to finish starting, and a
     * failure on the timer that escaped would take the schedule with it and no reset would happen
     * again until a restart.
     *
     * @return whether the data is now the seeded data
     */
    private boolean seedQuietly() {
        try {
            resetAndSeed();
            return true;
        } catch (Exception e) {
            log.error("Demo: Failed to seed database", e);
            return false;
        }
    }

    private boolean schemaUnchanged() {
        try {
            if (!Files.exists(SCHEMA_HASH_FILE)) return false;
            var stored = Files.readString(SCHEMA_HASH_FILE).strip();
            return stored.equals(computeSchemaHash());
        } catch (Exception e) {
            log.warn("Could not read schema hash, will re-seed", e);
            return false;
        }
    }

    private void writeSchemaHash() {
        try {
            Files.writeString(SCHEMA_HASH_FILE, computeSchemaHash());
        } catch (Exception e) {
            log.warn("Could not write schema hash file", e);
        }
    }

    private String computeSchemaHash() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = getClass().getResourceAsStream("/database/version")) {
                if (is != null) digest.update(is.readAllBytes());
            }
            for (int i = 1; ; i++) {
                try (InputStream is = getClass().getResourceAsStream("/database/postgresql/1/patch_" + i + ".sql")) {
                    if (is == null) break;
                    digest.update(is.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to compute schema hash", e);
        }
    }

    private void wipeDatabase() {
        String schema = databaseConfig.schema();
        query("DROP SCHEMA IF EXISTS " + schema + " CASCADE;").single().delete();
        query("CREATE SCHEMA " + schema + ";").single().insert();
        try {
            SqlUpdater.builder(dataSource, PostgreSql.get())
                    .setReplacements(new QueryReplacement("ember_schema", schema))
                    .setSchemas(schema)
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to re-run migrations after wipe", e);
        }
    }

    /**
     * Runs every seeder band in ascending order, joining after each band so the next one sees a
     * complete predecessor. The ordering comes from {@link DemoSeeder#order()} - never from the
     * iteration order of the injected set.
     */
    private void seedData() {
        var run = new DemoRunContext(passwordHasher.hash(DemoSeeder.PASSWORD));
        var bands = new TreeMap<>(seeders.stream().collect(Collectors.groupingBy(DemoSeeder::order)));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var band : bands.entrySet()) {
                List<CompletableFuture<Void>> tasks = band.getValue().stream()
                        .map(seeder -> CompletableFuture.runAsync(() -> seeder.seed(run), executor))
                        .toList();
                CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
            }
        }
        log.info("Demo: Created all user accounts (password: '{}')", DemoSeeder.PASSWORD);
        log.info("Demo: Admin login: admin@ember.local / {}", DemoSeeder.PASSWORD);
    }
}
