/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import de.chojo.sadu.core.configuration.DatabaseConfig;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

import javax.sql.DataSource;

/**
 * CLI entry point for {@code ./gradlew refreshDataTracking}.
 * Spins up a PostgreSQL testcontainer, runs all migrations, then refreshes
 * the tracking file from the resulting live schema.
 */
public final class DataTrackingRefreshCli {

    private static final Logger log = LoggerFactory.getLogger(DataTrackingRefreshCli.class);
    private static final String SCHEMA = "ember_schema";
    private static final Path TARGET_PATH = Path.of("src/main/resources/data_tracking.json");

    private DataTrackingRefreshCli() {}

    static void main(String[] args) throws Exception {
        log.info("Starting PostgreSQL testcontainer...");
        try (var container = new PostgreSQLContainer("postgres:17")
                .withDatabaseName("ember_tracking")
                .withUsername("test")
                .withPassword("test")
                .withStartupAttempts(4)) {
            container.start();
            log.info("Container running at {}:{}", container.getHost(), container.getFirstMappedPort());

            DataSource dataSource = buildDataSource(container);

            log.info("Running migrations into schema '{}'...", SCHEMA);
            SqlUpdater.builder(dataSource, PostgreSql.get())
                    .setReplacements(new QueryReplacement("ember_schema", SCHEMA))
                    .setSchemas(SCHEMA)
                    .execute();

            log.info("Reading schema and refreshing tracking file at {}", TARGET_PATH.toAbsolutePath());
            var refresher = new DataTrackingRefresher(new SchemaReader(dataSource, SCHEMA));
            var summary = refresher.refresh(TARGET_PATH);
            summary.log(log);

            if (summary.totalChanges() > 0) {
                log.info("Done — review changes with: git diff {}", TARGET_PATH);
            } else {
                log.info("Done — no changes detected.");
            }
        }
    }

    private static DataSource buildDataSource(PostgreSQLContainer container) {
        DatabaseConfig dbConfig = new DatabaseConfig() {
            @Override
            public String host() {
                return container.getHost();
            }

            @Override
            public String port() {
                return String.valueOf(container.getFirstMappedPort());
            }

            @Override
            public String user() {
                return container.getUsername();
            }

            @Override
            public String password() {
                return container.getPassword();
            }

            @Override
            public String database() {
                return container.getDatabaseName();
            }
        };

        return DataSourceCreator.create(PostgreSql.get())
                .configure(c -> c.withConfig(dbConfig).currentSchema(SCHEMA).applicationName("EmberTracking"))
                .create()
                .withMaximumPoolSize(2)
                .build();
    }
}
