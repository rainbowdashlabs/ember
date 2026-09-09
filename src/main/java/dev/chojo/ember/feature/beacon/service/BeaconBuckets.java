/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;

/**
 * Counts as they leave an instance, which is never as exact numbers.
 *
 * <p>A station's exact size, reported day after day, is a fingerprint. Anybody holding the metrics
 * and the public station directory can match a trajectory against the stations they can already see
 * and put a name to the identifier within a few weeks, which would undo the one promise the metrics
 * identifier makes. A bucket keeps the trend and loses the match.
 *
 * <p>Member counts use the buckets the public station card already uses, because a scheme this
 * project has already decided is safe to publish is the right one to reuse.
 */
public final class BeaconBuckets {

    private BeaconBuckets() {}

    /** The buckets the public station directory already publishes member counts in. */
    public static String members(int actual) {
        return DiscoveryStationCard.bucketMemberCount(actual);
    }

    /**
     * Buckets for the counts that have no published precedent: accounts, stations and inventory.
     *
     * <p>Wider as they grow, because the difference between six and seven says something about a
     * small station and the difference between six hundred and seven hundred says nothing about a
     * large one.
     */
    public static String size(int actual) {
        if (actual == 0) return "0";
        if (actual < 10) return "<10";
        if (actual < 50) return "10-50";
        if (actual < 200) return "50-200";
        if (actual < 1000) return "200-1000";
        return "1000+";
    }
}
