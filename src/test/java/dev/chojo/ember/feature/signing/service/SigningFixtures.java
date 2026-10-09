/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Callers racing each other and row counts, for the signing key tests that run against the database.
 */
final class SigningFixtures {
    private SigningFixtures() {}

    /**
     * Runs the same call on several threads, released at the same moment.
     *
     * @param callers how many threads call
     * @param call    what each of them calls
     * @param <T>     what the call returns
     * @return every caller's result
     */
    static <T> List<T> concurrently(int callers, Supplier<T> call) throws Exception {
        var start = new CountDownLatch(1);
        var results = new ArrayList<CompletableFuture<T>>();
        try (var executor = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                results.add(CompletableFuture.supplyAsync(
                        () -> {
                            awaitQuietly(start);
                            return call.get();
                        },
                        executor));
            }
            start.countDown();
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).get(2, TimeUnit.MINUTES);
        }
        return results.stream().map(CompletableFuture::join).toList();
    }

    /**
     * @param sql a query that selects one row with a count named {@code n}
     * @return that count
     */
    static int count(String sql) {
        return query(sql).single(call()).map(row -> row.getInt("n")).first().orElseThrow();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
