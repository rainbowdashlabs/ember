/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/**
 * Tracks recently-seen federation request nonces per partner, so an attacker
 * who captures a signed envelope cannot replay it within the timestamp
 * acceptance window.
 * <p>
 * Entries expire after {@code 2 × MAX_TIMESTAMP_DRIFT} (10 minutes), which is
 * enough to cover any request the signing layer would still accept. The cache
 * is per-process; clustered deployments would need a shared backing store.
 * <p>
 * Recording a nonce is a single atomic insert, so two copies of the same request
 * arriving at once cannot both pass. A nonce is never dropped before its window
 * ends: the cache has no size-based eviction, since evicting a live nonce would
 * reopen it for replay. Instead, once 500,000 nonces are held, new
 * requests are refused until older ones expire. Only requests with a valid
 * partner signature reach the cache, so filling it takes that many signed requests
 * within ten minutes.
 */
@Singleton
public class FederationReplayCache {
    private static final Logger log = LoggerFactory.getLogger(FederationReplayCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final long MAX_ENTRIES = 500_000L;

    private final Cache<NonceKey, Boolean> seen;
    private final long maxEntries;

    @Inject
    public FederationReplayCache() {
        this(MAX_ENTRIES);
    }

    /**
     * Builds a cache that holds at most {@code maxEntries} nonces, so a test can fill it without
     * recording the production number of requests.
     */
    FederationReplayCache(long maxEntries) {
        this.seen = Caffeine.newBuilder().expireAfterWrite(TTL).build();
        this.maxEntries = maxEntries;
    }

    /**
     * Records {@code (partnerId, nonce)} as seen. Returns {@code true} if this is
     * the first time the pair has been observed within the TTL window, and
     * {@code false} if it is a replay, or if the cache is full and the nonce
     * therefore could not be remembered, either of which is to be rejected.
     */
    public boolean checkAndRemember(int partnerId, UUID nonce) {
        if (isFull()) {
            log.warn("Rejected federation nonce {} from partner {}: replay cache is full", nonce, partnerId);
            return false;
        }
        if (seen.asMap().putIfAbsent(new NonceKey(partnerId, nonce), Boolean.TRUE) != null) {
            log.warn("Rejected replayed federation nonce {} from partner {}", nonce, partnerId);
            return false;
        }
        log.debug("Recorded federation nonce {} for partner {}", nonce, partnerId);
        return true;
    }

    private boolean isFull() {
        if (seen.estimatedSize() < maxEntries) return false;
        seen.cleanUp();
        return seen.estimatedSize() >= maxEntries;
    }

    /**
     * Pair of partner id and request nonce. The partner id is included so that
     * two distinct partners can independently mint the same nonce without
     * interference.
     */
    public record NonceKey(int partnerId, UUID nonce) {}
}
