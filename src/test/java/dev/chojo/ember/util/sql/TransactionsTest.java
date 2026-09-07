/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes grouped into one transaction, run through the repositories exactly as a service runs them.
 */
class TransactionsTest extends RepositoryTestBase {

    @Test
    void whatTheBodyWroteStandsOnceItReturns() {
        var station = Transactions.call(() -> stationRepo.create("Transaction Commit"));

        assertTrue(stationRepo.findById(station.id()).isPresent());
    }

    /**
     * The point of the whole thing: a body that gives up halfway leaves nothing behind, not even the
     * rows it had already written.
     */
    @Test
    void whatTheBodyWroteIsGoneWhenItGivesUpHalfway() {
        var written = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> Transactions.run(() -> {
                    written.set(stationRepo.create("Transaction Rollback").id());
                    throw new IllegalStateException("halfway");
                }));

        assertTrue(stationRepo.findById(written.get()).isEmpty());
    }

    /** A body inside a body joins the transaction that is running rather than opening a second. */
    @Test
    void aNestedBodyRollsBackWithTheOneAroundIt() {
        var written = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> Transactions.run(() -> {
                    Transactions.run(() ->
                            written.set(stationRepo.create("Transaction Nested").id()));
                    throw new IllegalStateException("halfway");
                }));

        assertTrue(stationRepo.findById(written.get()).isEmpty());
    }
}
