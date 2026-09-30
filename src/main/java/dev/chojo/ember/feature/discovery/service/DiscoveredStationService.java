/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.entity.CachedDiscoveryStation;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * The stations other instances published for discovery, as this instance last fetched them: the
 * list a member browses, and the picker an editor chooses a partner station from.
 */
@Singleton
public class DiscoveredStationService {
    private final DiscoveryStationCacheRepository cache;
    private final FederationRepository federation;

    @Inject
    public DiscoveredStationService(DiscoveryStationCacheRepository cache, FederationRepository federation) {
        this.cache = cache;
        this.federation = federation;
    }

    private static boolean matchesQuery(String name, String query) {
        if (query == null || query.isBlank()) return true;
        if (name == null) return false;
        return name.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private static DiscoveredStationResponse toResponse(CachedDiscoveryStation cached) {
        var card = cached.card();
        return new DiscoveredStationResponse(
                card.stationUid(),
                card.name(),
                card.slogan(),
                card.logoUrl(),
                card.country(),
                card.region(),
                card.city(),
                card.contactUrl(),
                card.tags(),
                card.memberCount(),
                card.publishedAt(),
                card.addressLine(),
                card.latitude(),
                card.longitude(),
                cached.instancePublicKey(),
                cached.fetchedAt());
    }

    public List<DiscoveredStationResponse> cachedStations() {
        return cache.findAll().stream()
                .map(DiscoveredStationService::toResponse)
                .toList();
    }

    /**
     * The stations an editor may pick as a partner: the station's own federation partners first,
     * then the discovered stations, each named once.
     *
     * @param stationId the editor's station, whose partners come first
     * @param query     a fragment of the name, or null for any
     * @param limit     how many at most, held between 1 and 50
     */
    public List<StationPickerResult> picker(int stationId, String query, int limit) {
        int held = Math.clamp(limit, 1, 50);
        LinkedHashMap<String, StationPickerResult> byUid = new LinkedHashMap<>();
        for (var partner : federation.findActivePartnerSummaries(stationId)) {
            if (matchesQuery(partner.name(), query)) {
                byUid.put(
                        partner.uid().toString(),
                        new StationPickerResult(partner.uid().toString(), partner.name(), null, null, null, true));
            }
        }
        for (var cached : cache.searchForPicker(query, held)) {
            var card = cached.card();
            byUid.putIfAbsent(
                    card.stationUid(),
                    new StationPickerResult(
                            card.stationUid(), card.name(), card.city(), card.country(), card.logoUrl(), true));
        }
        List<StationPickerResult> results = new ArrayList<>(byUid.values());
        return results.size() > held ? results.subList(0, held) : results;
    }

    /**
     * Lightweight picker result row. {@code selectable} mirrors whether the cell should let the
     * editor pick this station; the discovery cache only contains stations that already opted in
     * to public discovery, so this is always {@code true} today. The flag is part of the contract so
     * the frontend stays stable should the picker widen to partners that are not discoverable.
     */
    public record StationPickerResult(
            String stationUid, String name, String city, String country, String logoUrl, boolean selectable) {}

    public record DiscoveredStationResponse(
            String stationUid,
            String name,
            String slogan,
            String logoUrl,
            String country,
            String region,
            String city,
            String contactUrl,
            List<String> tags,
            String memberCount,
            Instant publishedAt,
            String addressLine,
            BigDecimal latitude,
            BigDecimal longitude,
            String instancePublicKey,
            Instant fetchedAt) {}
}
