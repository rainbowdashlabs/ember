/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * What of a station is on the public web, part by part.
 *
 * @param knowledgeBase its wiki
 * @param calendar      its appointments
 * @param pages         at least one listed page
 * @param waitlist      a waiting list that takes registrations
 * @param blog          at least one news entry on its blog
 */
public record PublicOffer(boolean knowledgeBase, boolean calendar, boolean pages, boolean waitlist, boolean blog) {

    /**
     * Whether nothing of the station is public, so a link to its public page would lead nowhere.
     */
    public boolean isEmpty() {
        return !knowledgeBase && !calendar && !pages && !waitlist && !blog;
    }

    /**
     * The offer as the discovery page names it: the wiki only where the station chose to show it there.
     *
     * @param station the station this offer belongs to
     * @return the offer with the wiki left out where the station keeps it off its discovery card
     */
    public PublicOffer inDiscoveryOf(Station station) {
        if (station.discoveryShowKb()) return this;
        return new PublicOffer(false, calendar, pages, waitlist, blog);
    }
}
