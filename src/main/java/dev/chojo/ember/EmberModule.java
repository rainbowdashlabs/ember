/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.v1.AttendanceRoutes;
import dev.chojo.ember.api.v1.AuthRoutes;
import dev.chojo.ember.api.v1.EventRoutes;
import dev.chojo.ember.api.v1.ExchangeRoutes;
import dev.chojo.ember.api.v1.FormRoutes;
import dev.chojo.ember.api.v1.InventoryCheckRoutes;
import dev.chojo.ember.api.v1.InventoryRoutes;
import dev.chojo.ember.api.v1.ManagedMemberRoutes;
import dev.chojo.ember.api.v1.MemberGroupRoutes;
import dev.chojo.ember.api.v1.MemberImportRoutes;
import dev.chojo.ember.api.v1.MemberRoutes;
import dev.chojo.ember.api.v1.NewsRoutes;
import dev.chojo.ember.api.v1.NotificationRoutes;
import dev.chojo.ember.api.v1.ProcurementRoutes;
import dev.chojo.ember.api.v1.ProfileFieldChangeRoutes;
import dev.chojo.ember.api.v1.ProfileFieldRoutes;
import dev.chojo.ember.api.v1.RegistrationCodeRoutes;
import dev.chojo.ember.api.v1.SavedFilterRoutes;
import dev.chojo.ember.api.v1.SessionRoutes;
import dev.chojo.ember.api.v1.StationApplicationRoutes;
import dev.chojo.ember.api.v1.StationManageRoutes;
import dev.chojo.ember.api.v1.StationMemberRoutes;
import dev.chojo.ember.api.v1.StationRoutes;
import dev.chojo.ember.api.v1.StatisticsRoutes;
import dev.chojo.ember.api.v1.UserSettingsRoutes;
import dev.chojo.ember.api.v1.UserTagRoutes;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

import javax.sql.DataSource;

public class EmberModule extends AbstractModule {
    private static final Logger log = LoggerFactory.getLogger(EmberModule.class);
    private final Conf conf;

    public EmberModule(Conf conf) {
        this.conf = conf;
    }

    @Override
    protected void configure() {
        Multibinder<Routes> routesBinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesBinder.addBinding().to(AuthRoutes.class);
        routesBinder.addBinding().to(MemberRoutes.class);
        routesBinder.addBinding().to(SessionRoutes.class);
        routesBinder.addBinding().to(StationRoutes.class);
        routesBinder.addBinding().to(StationMemberRoutes.class);
        routesBinder.addBinding().to(AttendanceRoutes.class);
        routesBinder.addBinding().to(InventoryRoutes.class);
        routesBinder.addBinding().to(ProfileFieldRoutes.class);
        routesBinder.addBinding().to(ProfileFieldChangeRoutes.class);
        routesBinder.addBinding().to(MemberGroupRoutes.class);
        routesBinder.addBinding().to(RegistrationCodeRoutes.class);
        routesBinder.addBinding().to(StationManageRoutes.class);
        routesBinder.addBinding().to(EventRoutes.class);
        routesBinder.addBinding().to(SavedFilterRoutes.class);
        routesBinder.addBinding().to(InventoryCheckRoutes.class);
        routesBinder.addBinding().to(ManagedMemberRoutes.class);
        routesBinder.addBinding().to(StationApplicationRoutes.class);
        routesBinder.addBinding().to(StatisticsRoutes.class);
        routesBinder.addBinding().to(MemberImportRoutes.class);
        routesBinder.addBinding().to(NewsRoutes.class);
        routesBinder.addBinding().to(UserSettingsRoutes.class);
        routesBinder.addBinding().to(ExchangeRoutes.class);
        routesBinder.addBinding().to(ProcurementRoutes.class);
        routesBinder.addBinding().to(UserTagRoutes.class);
        routesBinder.addBinding().to(NotificationRoutes.class);
        routesBinder.addBinding().to(FormRoutes.class);
    }

    @Provides
    @Singleton
    Conf conf() {
        return conf;
    }

    @Provides
    @Singleton
    File config() {
        var config = conf.main();
        log.info(config.toString());
        return config;
    }

    @Provides
    @Singleton
    Database database(File config) {
        return config.database();
    }

    @Provides
    @Singleton
    Api api(File config) {
        return config.api();
    }

    @Provides
    @Singleton
    Mailing mailing(File config) {
        return config.mailing();
    }

    @Provides
    @Singleton
    Auth auth(File config) {
        return config.auth();
    }

    @Provides
    @Singleton
    Demo demo(File config) {
        return config.demo();
    }

    @Provides
    @Singleton
    DataSource dataSource(Database database) {
        return DataSourceCreator.create(PostgreSql.get())
                .configure(config -> config.withConfig(database)
                        .currentSchema(database.schema())
                        .applicationName("Ember"))
                .create()
                .withMaximumPoolSize(database.poolSize())
                .withMinimumIdle(1)
                .build();
    }

    @Provides
    @Singleton
    QueryConfiguration queryConfiguration(DataSource dataSource, Database database) throws SQLException, IOException {
        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setReplacements(new QueryReplacement("ember_schema", database.schema()))
                .setSchemas(database.schema())
                .execute();

        var config = QueryConfiguration.builder(dataSource)
                .setExceptionHandler(err -> log.error("Database query error", err))
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build();
        QueryConfiguration.setDefault(config);
        return config;
    }
}
