/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a published discovery card tells a reader to find the station's public page.
 */
class DiscoveryStationCardAddressTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static DiscoveryStationProjectionService service;

    @BeforeAll
    static void setup() {
        service = new DiscoveryStationProjectionService(stationRepo, clusterRepo, new Conf());
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
