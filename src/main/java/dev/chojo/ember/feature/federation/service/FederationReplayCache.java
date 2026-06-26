/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
 */
@Singleton
public class FederationReplayCache {
    private static final Logger log = LoggerFactory.getLogger(FederationReplayCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final long MAX_ENTRIES = 100_000L;

    private final Cache<NonceKey, Boolean> seen =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(MAX_ENTRIES).build();

    /**
     * Records {@code (partnerId, nonce)} as seen. Returns {@code true} if this is
     * the first time the pair has been observed within the TTL window, and
     * {@code false} if it is a replay that should be rejected.
     */
    public boolean checkAndRemember(int partnerId, UUID nonce) {
        var key = new NonceKey(partnerId, nonce);
        if (seen.getIfPresent(key) != null) {
            log.warn("Rejected replayed federation nonce {} from partner {}", nonce, partnerId);
            return false;
        }
        seen.put(key, Boolean.TRUE);
        log.debug("Recorded federation nonce {} for partner {}", nonce, partnerId);
        return true;
    }

    /**
     * Pair of partner id and request nonce. The partner id is included so that
     * two distinct partners can independently mint the same nonce without
     * interference.
     */
    public record NonceKey(int partnerId, UUID nonce) {}
}
