/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.entity.CachedDiscoveryStation;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.federation.entity.PublicPartnerSummary;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveredStationServiceTest {
    private static final Instant AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private final DiscoveryStationCacheRepository cache = mock(DiscoveryStationCacheRepository.class);
    private final FederationRepository federation = mock(FederationRepository.class);
    private final DiscoveredStationService service = new DiscoveredStationService(cache, federation);

    private static CachedDiscoveryStation cached(String uid, String name) {
        var card = new DiscoveryStationCard(
                uid, name, null, null, "DE", null, "Kiel", null, List.of(), null, AT, null, null, null, null, null);
        return new CachedDiscoveryStation("key", uid, card, AT);
    }

    @Test
    void theCachedStationsAreListedWithTheInstanceTheyCameFrom() {
        when(cache.findAll()).thenReturn(List.of(cached("s1", "Nord")));

        var stations = service.cachedStations();

        assertEquals("Nord", stations.getFirst().name());
        assertEquals("key", stations.getFirst().instancePublicKey());
    }

    @Test
    void thePickerNamesPartnersFirstAndEveryStationOnce() {
        when(federation.findActivePartnerSummaries(3))
                .thenReturn(List.of(
                        new PublicPartnerSummary(PARTNER, "Nordwache", null, null),
                        new PublicPartnerSummary(UUID.randomUUID(), "Süd", null, null),
                        new PublicPartnerSummary(UUID.randomUUID(), null, null, null)));
        when(cache.searchForPicker(" Nord", 2))
                .thenReturn(List.of(cached(PARTNER.toString(), "Nordwache (cached)"), cached("s2", "Nordost")));

        var picked = service.picker(3, " Nord", 2);

        assertEquals(
                List.of(PARTNER.toString(), "s2"),
                picked.stream().map(p -> p.stationUid()).toList());
        assertEquals("Nordwache", picked.getFirst().name());
    }

    @Test
    void thePickerIsHeldToItsLimit() {
        when(federation.findActivePartnerSummaries(anyInt())).thenReturn(List.of());
        when(cache.searchForPicker(any(), anyInt()))
                .thenReturn(List.of(cached("s1", "a"), cached("s2", "b"), cached("s3", "c")));

        assertEquals(1, service.picker(3, null, 0).size());
    }
}
