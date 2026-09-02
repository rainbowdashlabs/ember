/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.queries.api.configuration.ActiveQueryConfiguration;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.queries.api.configuration.context.QueryContext;
import de.chojo.sadu.queries.api.query.ParsedQuery;
import de.chojo.sadu.queries.configuration.ConnectedQueryConfigurationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sql.DataSource;

/**
 * Runs a group of writes as one, so a failure halfway leaves nothing behind.
 *
 * <p>Repositories reach the database through the static {@code Query.query(...)}, which always asks
 * for the default configuration. A transaction opened in a service would therefore never be seen by
 * the repositories it calls. {@link #threadScoped(QueryConfiguration)} closes that gap: the default
 * becomes a configuration that answers with the transaction of the calling thread while one is
 * running, and with the plain configuration otherwise. Nothing outside {@link #run(Runnable)} and
 * {@link #call(Supplier)} ever sees a difference.
 */
public final class Transactions {
    private static final Logger log = LoggerFactory.getLogger(Transactions.class);
    private static final ThreadLocal<ConnectedQueryConfigurationImpl> ACTIVE = new ThreadLocal<>();

    private Transactions() {}

    /**
     * Wraps a configuration so that queries on a thread inside {@link #run(Runnable)} run on that
     * thread's transaction. Install the result as the default configuration.
     *
     * @param base the configuration to fall back to outside a transaction
     * @return the configuration to hand to {@code QueryConfiguration.setDefault}
     */
    public static QueryConfiguration threadScoped(QueryConfiguration base) {
        return new ThreadScoped(base);
    }

    /**
     * Runs the body inside one transaction, committing when it returns and rolling back when it
     * throws. A body started while a transaction is already running joins that one instead of
     * opening a second.
     *
     * @param body the writes that belong together
     */
    public static void run(Runnable body) {
        call(() -> {
            body.run();
            return null;
        });
    }

    /**
     * The same as {@link #run(Runnable)} for a body that produces a value.
     *
     * @param body the writes that belong together
     * @param <T>  what the body returns
     * @return whatever the body returned
     */
    public static <T> T call(Supplier<T> body) {
        if (ACTIVE.get() != null) return body.get();

        var transaction = QueryConfiguration.getDefault().withSingleTransaction();
        ACTIVE.set(transaction);
        try (transaction) {
            try {
                return body.get();
            } catch (RuntimeException | Error e) {
                rollback(transaction);
                throw e;
            }
        } finally {
            ACTIVE.remove();
        }
    }

    /**
     * Undoes what the failed body wrote.
     *
     * <p>Closing the transaction commits unless a database error was recorded on it, and a body that
     * failed for any other reason records nothing. So the rollback is spelled out here rather than
     * left to the close that follows it.
     */
    private static void rollback(ConnectedQueryConfiguration transaction) {
        try {
            transaction.connection().rollback();
        } catch (SQLException | RuntimeException e) {
            log.warn("Could not roll back a transaction after a failed write", e);
        }
    }

    /**
     * The default configuration once a transaction can be running: the thread's transaction while
     * there is one, the plain configuration otherwise.
     */
    private record ThreadScoped(QueryConfiguration base) implements QueryConfiguration {
        private QueryConfiguration current() {
            var active = ACTIVE.get();
            return active != null ? active : base;
        }

        @Override
        public ActiveQueryConfiguration forQuery(QueryContext context) {
            return current().forQuery(context);
        }

        @Override
        public boolean atomic() {
            return current().atomic();
        }

        @Override
        public boolean throwExceptions() {
            return current().throwExceptions();
        }

        @Override
        public RowMapperRegistry rowMapperRegistry() {
            return current().rowMapperRegistry();
        }

        @Override
        public DataSource dataSource() {
            return current().dataSource();
        }

        @Override
        public Consumer<SQLException> exceptionHandler() {
            return current().exceptionHandler();
        }

        @Override
        public ParsedQuery query(String sql, Object... format) {
            return current().query(sql, format);
        }

        @Override
        public ConnectedQueryConfigurationImpl withSingleTransaction() {
            return base.withSingleTransaction();
        }

        @Override
        public ConnectedQueryConfiguration withConnection(Connection connection) {
            return base.withConnection(connection);
        }
    }
}
