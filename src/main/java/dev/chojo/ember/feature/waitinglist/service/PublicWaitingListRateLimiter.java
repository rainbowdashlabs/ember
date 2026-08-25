/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Guards the public endpoints that make this instance send mail to an address the request chose:
 * registering on a waiting list, through an invite or through a list that is open to everyone.
 *
 * <p>Two buckets, because the two abuses have different shapes. One caller registering over and
 * over is caught per address. An invite printed on a poster, or an open list, being worked by many
 * callers at once is caught per registration route, which is what keeps one link from draining the
 * instance's daily mail budget and taking password resets down with it.
 *
 * <p>A development instance keeps the machinery with a capacity nothing reaches: the end-to-end
 * suite registers through the same invite on every run, and a limit meant for the open internet
 * would make it fail as though the list were broken.
 */
@Singleton
public class PublicWaitingListRateLimiter {

    /** Registrations admitted from one address per hour. A family signing up together fits. */
    public static final int PER_ADDRESS_CAPACITY = 5;

    /** Registrations admitted through one invite or one open list per hour, whoever sends them. */
    public static final int PER_INVITE_CAPACITY = 30;

    private static final int DEV_CAPACITY = 1_000_000;
    private static final Duration PRUNE_AFTER = Duration.ofHours(2);

    private final LeakyBucket perAddress;
    private final LeakyBucket perInvite;

    @Inject
    public PublicWaitingListRateLimiter(Demo demoConfig) {
        this(
                Clock.systemUTC(),
                demoConfig.dev() ? DEV_CAPACITY : PER_ADDRESS_CAPACITY,
                demoConfig.dev() ? DEV_CAPACITY : PER_INVITE_CAPACITY);
    }

    public PublicWaitingListRateLimiter(Clock clock) {
        this(clock, PER_ADDRESS_CAPACITY, PER_INVITE_CAPACITY);
    }

    private PublicWaitingListRateLimiter(Clock clock, int addressCapacity, int inviteCapacity) {
        this.perAddress =
                new LeakyBucket(addressCapacity, Duration.ofMinutes(60 / PER_ADDRESS_CAPACITY), PRUNE_AFTER, clock);
        this.perInvite =
                new LeakyBucket(inviteCapacity, Duration.ofMinutes(60 / PER_INVITE_CAPACITY), PRUNE_AFTER, clock);
    }

    /**
     * @param clientIp the resolved address of the caller
     * @param route    what the registration comes through: an invite code, or the list itself when
     *                 it is open to everyone
     * @return empty when the registration is allowed, or the seconds until the next refill when
     * the caller should be served {@code 429} with {@code Retry-After}
     */
    public Optional<Long> tryAcquire(String clientIp, String route) {
        var addressRetry = perAddress.tryAcquire("address:" + clientIp);
        if (addressRetry.isPresent()) return addressRetry;
        return perInvite.tryAcquire("route:" + route);
    }
}
