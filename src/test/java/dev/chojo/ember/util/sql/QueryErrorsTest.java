/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A failed query is named by the database's own error, on one line, however the query library
 * wrapped it.
 */
class QueryErrorsTest {

    @Test
    void theDatabasesOwnErrorNamesTheFailure() {
        var database = new SQLException("ERROR: relation \"nowhere\" does not exist\n  Position: 15");
        var wrapped = new SQLException("An error occurred: ...\nWhile executing query:\nSELECT 1", database);

        assertEquals("ERROR: relation \"nowhere\" does not exist", QueryErrors.summary(wrapped));
    }

    @Test
    void anErrorWithoutWordsIsNamedByItsKind() {
        assertEquals("SQLException", QueryErrors.summary(new SQLException()));
    }
}
