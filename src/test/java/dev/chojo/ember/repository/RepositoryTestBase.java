/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.core.configuration.DatabaseConfig;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

@Tag("database")
@Testcontainers
public abstract class RepositoryTestBase {
    private static final String SCHEMA = "ember";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("ember_test")
            .withUsername("test")
            .withPassword("test");

    protected static AccountRepository accountRepo;
    protected static StationRepository stationRepo;
    protected static StationMemberRepository stationMemberRepo;
    protected static AttendanceRepository attendanceRepo;
    protected static InventoryRepository inventoryRepo;
    protected static MemberGroupRepository memberGroupRepo;
    protected static ProfileFieldRepository profileFieldRepo;
    protected static RegistrationCodeRepository registrationCodeRepo;
    protected static EventRepository eventRepo;
    protected static SavedFilterRepository savedFilterRepo;
    protected static InventoryCheckRepository inventoryCheckRepo;

    @BeforeAll
    static void setupDatabase() throws Exception {
        DatabaseConfig dbConfig = new DatabaseConfig() {
            @Override
            public String host() {
                return PG.getHost();
            }

            @Override
            public String port() {
                return String.valueOf(PG.getFirstMappedPort());
            }

            @Override
            public String user() {
                return PG.getUsername();
            }

            @Override
            public String password() {
                return PG.getPassword();
            }

            @Override
            public String database() {
                return PG.getDatabaseName();
            }
        };

        DataSource dataSource = DataSourceCreator.create(PostgreSql.get())
                .configure(config ->
                        config.withConfig(dbConfig).currentSchema(SCHEMA).applicationName("EmberTest"))
                .create()
                .withMaximumPoolSize(2)
                .build();

        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setReplacements(new QueryReplacement("ember_schema", SCHEMA))
                .setSchemas(SCHEMA)
                .execute();

        var config = QueryConfiguration.builder(dataSource)
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build();
        QueryConfiguration.setDefault(config);

        accountRepo = new AccountRepository();
        stationRepo = new StationRepository();
        stationMemberRepo = new StationMemberRepository();
        attendanceRepo = new AttendanceRepository();
        inventoryRepo = new InventoryRepository();
        memberGroupRepo = new MemberGroupRepository();
        profileFieldRepo = new ProfileFieldRepository();
        registrationCodeRepo = new RegistrationCodeRepository();
        eventRepo = new EventRepository();
        savedFilterRepo = new SavedFilterRepository();
        inventoryCheckRepo = new InventoryCheckRepository();
    }
}
