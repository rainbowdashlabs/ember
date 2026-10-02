/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.lifecycle.TaskScheduler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fans a federated read out to several partners in parallel and merges their results.
 * Partners on this instance are served by the local fetcher, partners on another instance by the
 * remote fetcher. A failing partner only loses its own results, all other partners still contribute.
 *
 * <p>Each partner is asked on a virtual thread of its own, since asking a remote partner blocks on
 * the network, and gets fifteen seconds to answer. A partner that takes longer is reported as
 * {@link PartnerOutcome#TIMED_OUT} and the others are answered without it.
 */
@Singleton
public class FederationFanout {
    private static final Logger log = LoggerFactory.getLogger(FederationFanout.class);
    private static final Duration PARTNER_TIMEOUT = Duration.ofSeconds(15);

    private final Executor executor;
    private final Duration partnerTimeout;

    @Inject
    public FederationFanout(TaskScheduler scheduler) {
        this(scheduler.executor(), PARTNER_TIMEOUT);
    }

    /**
     * Builds the fan-out with its own limit on how long one partner may take, so a test can stand in
     * a stalled partner without waiting out the production limit.
     */
    FederationFanout(Executor executor, Duration partnerTimeout) {
        this.executor = executor;
        this.partnerTimeout = partnerTimeout;
    }

    /**
     * Queries all given partners in parallel through one fetcher that serves every partner, wherever
     * it lives.
     *
     * @param partners the partners to query
     * @param fetcher  fetcher for any partner
     * @param <T>      the result element type
     * @return every partner's answer, in partner order
     */
    public <T> FanoutResult<T> fanOut(List<FederationPartner> partners, Function<FederationPartner, List<T>> fetcher) {
        var pending = partners.stream()
                .map(partner -> CompletableFuture.supplyAsync(() -> fetcher.apply(partner), executor)
                        .orTimeout(partnerTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .handle((items, error) -> answer(partner, items, error)))
                .toList();
        return new FanoutResult<>(pending.stream().map(CompletableFuture::join).toList());
    }

    private static <T> PartnerAnswer<T> answer(FederationPartner partner, List<T> items, Throwable error) {
        if (error == null) {
            return new PartnerAnswer<>(partner, PartnerOutcome.ANSWERED, items == null ? List.of() : items);
        }
        var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof TimeoutException) {
            log.warn("Federation partner {} did not answer in time", partner.id());
            return new PartnerAnswer<>(partner, PartnerOutcome.TIMED_OUT, List.of());
        }
        log.error("Federation partner {} could not be queried", partner.id(), cause);
        return new PartnerAnswer<>(partner, PartnerOutcome.FAILED, List.of());
    }

    /** How asking one partner ended. */
    public enum PartnerOutcome {
        /** The partner answered; an empty answer means it has nothing to show. */
        ANSWERED,
        /** Asking the partner failed before it could answer. */
        FAILED,
        /** The partner did not answer within the time a fan-out gives it. */
        TIMED_OUT
    }

    /**
     * What one partner contributed to a fan-out.
     *
     * @param partner the partner that was asked
     * @param outcome how asking it ended
     * @param items   what it answered, empty unless the outcome is {@link PartnerOutcome#ANSWERED}
     * @param <T>     the result element type
     */
    public record PartnerAnswer<T>(FederationPartner partner, PartnerOutcome outcome, List<T> items) {}

    /**
     * Every partner's answer to one fan-out.
     *
     * @param answers one entry per partner, in the order the partners were given
     * @param <T>     the result element type
     */
    public record FanoutResult<T>(List<PartnerAnswer<T>> answers) {

        /** The results of every partner that answered, merged in partner order into a list the caller may change. */
        public List<T> items() {
            return answers.stream()
                    .flatMap(answer -> answer.items().stream())
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        /** The partners that failed or timed out, as opposed to those that answered with nothing. */
        public List<FederationPartner> unanswered() {
            return answers.stream()
                    .filter(answer -> answer.outcome() != PartnerOutcome.ANSWERED)
                    .map(PartnerAnswer::partner)
                    .toList();
        }
    }
}
