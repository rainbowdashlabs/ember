/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Keeps nonces in memory until their requests could no longer be accepted.
 *
 * <p>Used for federation, where every request is signed by a known partner and the process restarting
 * is the only thing that forgets. A clustered deployment would need a shared store instead.
 *
 * <p>A nonce is never dropped before its window ends: the store has no size-based eviction, since
 * evicting a live nonce would reopen it for replay. Instead, once it holds its limit of 500,000
 * nonces, new requests are refused until older ones expire. Only requests with a valid signature
 * reach the store, so filling it takes that many signed requests within one window.
 */
@Singleton
public class MemoryReplayStore implements ReplayStore {
    private static final Logger log = LoggerFactory.getLogger(MemoryReplayStore.class);
    private static final long MAX_ENTRIES = 500_000L;

    private final Cache<Key, Instant> seen;
    private final long maxEntries;

    @Inject
    public MemoryReplayStore() {
        this(MAX_ENTRIES);
    }

    /**
     * Builds a store that holds at most {@code maxEntries} nonces, so a test can fill it without
     * recording the production number of requests.
     *
     * @param maxEntries how many nonces may be held at once
     */
    public MemoryReplayStore(long maxEntries) {
        this.maxEntries = maxEntries;
        this.seen = Caffeine.newBuilder().expireAfter(new UntilExpiry()).build();
    }

    @Override
    public boolean firstSighting(String scope, String nonce, Instant expiresAt) {
        if (isFull()) {
            log.warn("Rejected nonce {} of {}: the replay store is full", nonce, scope);
            return false;
        }
        if (seen.asMap().putIfAbsent(new Key(scope, nonce), expiresAt) != null) {
            log.warn("Rejected replayed nonce {} of {}", nonce, scope);
            return false;
        }
        return true;
    }

    private boolean isFull() {
        if (seen.estimatedSize() < maxEntries) return false;
        seen.cleanUp();
        return seen.estimatedSize() >= maxEntries;
    }

    /**
     * A nonce as seen from one sender. The scope is part of the key so two senders can mint the same
     * nonce without interfering.
     */
    private record Key(String scope, String nonce) {}

    /** Lets each nonce live exactly until the moment the caller named. */
    private record UntilExpiry() implements Expiry<Key, Instant> {
        @Override
        public long expireAfterCreate(Key key, Instant expiresAt, long currentTime) {
            return Math.max(0, Duration.between(Instant.now(), expiresAt).toNanos());
        }

        @Override
        public long expireAfterUpdate(Key key, Instant expiresAt, long currentTime, long currentDuration) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(Key key, Instant expiresAt, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
