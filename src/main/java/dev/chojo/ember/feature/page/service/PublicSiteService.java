/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.api.refusal.PageRefusal;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.federation.entity.PublicPartnerSummary;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Which station's public pages answer at all, and the partner stations their cells name.
 */
@Singleton
public class PublicSiteService {
    private final StationRepository stations;
    private final FederationRepository federation;
    private final DiscoveryStationCacheRepository discoveryCache;

    @Inject
    public PublicSiteService(
            StationRepository stations,
            FederationRepository federation,
            DiscoveryStationCacheRepository discoveryCache) {
        this.stations = stations;
        this.federation = federation;
        this.discoveryCache = discoveryCache;
    }

    /**
     * The station whose public site an address names, where it has opened its pages.
     *
     * <p>The switch that opens a station's pages to the world is asked here, so turning it off
     * closes every page to anybody who still has an address, not only the way in. A page reached by
     * its own link is not part of that site and asks nothing of the switch.
     *
     * @param address the station as the address names it, by identifier or by its public slug
     * @return the station's id
     */
    public int openStation(String address) {
        int stationId = stations.resolveAddressedId(address)
                .orElseThrow(PageRefusal.STATION_NOT_HERE_BEHIND_PUBLIC_PAGE::raise);
        boolean open =
                stations.findById(stationId).map(Station::publicPagesEnabled).orElse(false);
        if (!open) throw PageRefusal.PUBLIC_PAGES_SWITCHED_OFF.raise();
        return stationId;
    }

    /**
     * The station a page reached by its link belongs to, where it still lets anybody outside in.
     *
     * <p>Answered as a link nobody knows rather than as a station that has closed, because the
     * reader holds a link and is owed nothing about which of the two it was.
     */
    public Station sharedPageStation(StationPage page) {
        return stations.findById(page.stationId())
                .filter(Station::publicPagesEnabled)
                .orElseThrow(PageRefusal.PAGE_LINK_UNKNOWN::raise);
    }

    /**
     * The partner stations the station's cells may name.
     *
     * <p>Without a list, every active federation partner. With one, each requested station once
     * and in the order asked: first from the station's own partners, then from the public discovery
     * cache, so a cell can name any publicly discoverable station. A station found in neither is
     * left out.
     *
     * @param requestedUids the stations asked for, separated by commas, or null for all partners
     */
    public List<PublicPartnerSummary> partners(int stationId, @Nullable String requestedUids) {
        var partners = federation.findActivePartnerSummaries(stationId);
        if (requestedUids == null || requestedUids.isBlank()) return partners;
        var requested = Arrays.stream(requestedUids.split(","))
                .map(String::trim)
                .filter(uid -> !uid.isEmpty())
                .distinct()
                .toList();
        var byUid = new LinkedHashMap<String, PublicPartnerSummary>();
        partners.forEach(partner -> byUid.put(partner.uid().toString(), partner));
        var unresolved =
                requested.stream().filter(uid -> !byUid.containsKey(uid)).toList();
        if (!unresolved.isEmpty()) {
            for (var cached : discoveryCache.findByStationUids(unresolved)) {
                var card = cached.card();
                byUid.put(
                        card.stationUid(),
                        new PublicPartnerSummary(UUID.fromString(card.stationUid()), card.name(), null, null));
            }
        }
        var ordered = new ArrayList<PublicPartnerSummary>(requested.size());
        requested.stream().map(byUid::get).filter(match -> match != null).forEach(ordered::add);
        return ordered;
    }
}
