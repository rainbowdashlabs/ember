/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Fans a federated read out to several partners in parallel and merges their results.
 * Partners on this instance are served by the local fetcher, partners on another instance by the
 * remote fetcher. A failing partner only loses its own results, all other partners still contribute.
 */
@Singleton
public class FederationFanout {
    private static final Logger log = LoggerFactory.getLogger(FederationFanout.class);

    /**
     * Queries all given partners in parallel and returns their results merged in partner order.
     *
     * @param partners      the partners to query
     * @param localFetcher  fetcher for partners living on this instance
     * @param remoteFetcher fetcher for partners living on another instance
     * @param <T>           the result element type
     * @return the merged results of all partners that answered successfully
     */
    public <T> List<T> fanOut(
            List<FederationPartner> partners,
            Function<FederationPartner, List<T>> localFetcher,
            Function<FederationPartner, List<T>> remoteFetcher) {
        List<CompletableFuture<List<T>>> futures = partners.stream()
                .map(partner -> CompletableFuture.supplyAsync(
                        () -> partner.isRemote() ? remoteFetcher.apply(partner) : localFetcher.apply(partner)))
                .toList();
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation fetch", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation results", e);
            }
        }
        return result;
    }
}
