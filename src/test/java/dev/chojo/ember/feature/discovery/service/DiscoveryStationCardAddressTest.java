/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.PublicOffer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.PublicStationInfoService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where a published discovery card tells a reader to find the station's public page.
 */
class DiscoveryStationCardAddressTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static DiscoveryStationProjectionService service;
    private static StationLogoService logos;
    private static PublicStationInfoService publicInfo;

    @TempDir
    static Path configDir;

    @BeforeAll
    static void setup() {
        logos = mock(StationLogoService.class);
        publicInfo = mock(PublicStationInfoService.class);
        when(publicInfo.offer(any())).thenReturn(new PublicOffer(false, true, false, false, false));
        service =
                new DiscoveryStationProjectionService(stationRepo, clusterRepo, new Conf(configDir), logos, publicInfo);
    }

    private static Station publicStation(String name) {
        var station = stationRepo.create(name + " " + NAMES.incrementAndGet());
        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Hier", true);
        return station;
    }

    @Test
    void aStationWithNothingPublicSendsNoPublicPage() {
        var station = publicStation("Wache Ohne Seite");
        when(publicInfo.offer(argThat(s -> s != null && s.id() == station.id())))
                .thenReturn(new PublicOffer(false, false, false, false, false));

        var card = cardOf(station.id(), station.uid().toString());

        assertNull(card.contactUrl());
        stationRepo.delete(station.id());
    }

    @Test
    void aLogoIsAddressedOnlyForAStationThatHasOne() {
        var withLogo = publicStation("Wache Mit Logo");
        var withoutLogo = publicStation("Wache Ohne Logo");
        when(logos.exists(withLogo.id())).thenReturn(true);

        assertTrue(cardOf(withLogo.id(), withLogo.uid().toString())
                .logoUrl()
                .endsWith("/api/v1/public/stations/" + withLogo.uid() + "/logo"));
        assertNull(cardOf(withoutLogo.id(), withoutLogo.uid().toString()).logoUrl());

        stationRepo.delete(withLogo.id());
        stationRepo.delete(withoutLogo.id());
    }

    private static DiscoveryStationCard cardOf(int stationId, String uid) {
        return service.publicCards().stream()
                .filter(c -> c.stationUid().equals(uid))
                .findFirst()
                .orElseThrow(() -> new AssertionError("station " + stationId + " is on no card"));
    }

    @Test
    void aCardCarriesThePublicAddressAndLinksToThePublicPage() {
        var station = stationRepo.create("Wache Adresse " + NAMES.incrementAndGet());
        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Hier", true);
        stationRepo.updatePublicSlug(station.id(), "wache-adresse-" + NAMES.incrementAndGet());
        var slug = stationRepo.findById(station.id()).orElseThrow().publicSlug();

        var card = cardOf(station.id(), station.uid().toString());

        assertEquals(slug, card.publicSlug());
        assertEquals(slug, card.publicAddress());
        assertTrue(card.contactUrl().endsWith("/public/station/" + slug), card.contactUrl());

        stationRepo.delete(station.id());
    }

    @Test
    void aStationWithoutReadableAddressIsLinkedByItsIdentifier() {
        var station = stationRepo.create("Wache Ohne Adresse " + NAMES.incrementAndGet());
        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Hier", true);
        stationRepo.updatePublicSlug(station.id(), null);

        var card = cardOf(station.id(), station.uid().toString());

        assertNull(card.publicSlug());
        assertEquals(station.uid().toString(), card.publicAddress());
        assertTrue(card.contactUrl().endsWith("/public/station/" + station.uid()), card.contactUrl());

        stationRepo.delete(station.id());
    }

    @Test
    void aCardFromAPeerThatKnowsNoPublicAddressIsAddressedByItsIdentifier() {
        var card = new DiscoveryStationCard(
                "uid-alt",
                "Wache",
                null,
                null,
                "DE",
                null,
                "Musterstadt",
                null,
                List.of(),
                "<10",
                Instant.now(),
                null,
                null,
                null,
                null,
                null);

        assertNull(card.publicSlug());
        assertEquals("uid-alt", card.publicAddress());
    }

    @Test
    void aBlankPublicAddressCountsAsNone() {
        var card = new DiscoveryStationCard(
                "uid-blank",
                "Wache",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "<10",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                " ");

        assertEquals("uid-blank", card.publicAddress());
    }
}
