/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * A data source that counts the statements prepared on the connections it hands out.
 *
 * <p>Which is how a test proves that a piece of work costs a fixed number of round trips rather
 * than one per row: the count is taken where every query has to pass, below any repository.
 */
final class StatementCountingDataSource implements DataSource {
    private static final Set<String> STATEMENT_FACTORIES = Set.of("prepareStatement", "prepareCall", "createStatement");

    private final DataSource delegate;
    private final AtomicInteger statements = new AtomicInteger();

    StatementCountingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    /** The statements prepared so far. */
    int statements() {
        return statements.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return counting(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return counting(delegate.getConnection(username, password));
    }

    private Connection counting(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (STATEMENT_FACTORIES.contains(method.getName())) statements.incrementAndGet();
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
