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
 * What a discovery card says about the cluster a station answers to.
 */
class DiscoveryClusterGroupingTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static DiscoveryStationProjectionService service;

    @BeforeAll
    static void setup() {
        service = new DiscoveryStationProjectionService(stationRepo, clusterRepo, new Conf());
    }

    @Test
    void aStationCardNamesTheClusterItAnswersTo() {
        var cluster = clusterService.create("Kreisverband Karte " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Karte " + NAMES.incrementAndGet());
        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Wir sind da", true);

        var card = service.publicCards().stream()
                .filter(c -> c.stationUid().equals(station.uid().toString()))
                .findFirst()
                .orElseThrow();

        assertEquals(cluster.uid().toString(), card.clusterUid());
        assertEquals(cluster.name(), card.clusterName());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aStationOutsideAnyClusterCarriesNothing() {
        var station = stationRepo.create("Wache Allein " + NAMES.incrementAndGet());
        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Allein", true);

        var card = service.publicCards().stream()
                .filter(c -> c.stationUid().equals(station.uid().toString()))
                .findFirst()
                .orElseThrow();

        assertNull(card.clusterUid());
        assertNull(card.clusterName());

        stationRepo.delete(station.id());
    }

    @Test
    void aCardFromAPeerThatKnowsNothingOfClustersReadsAsOutsideOne() {
        var card = new DiscoveryStationCard(
                "uid",
                "Wache",
                "Beschreibung",
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
                null);

        assertNull(card.clusterUid(), "absent and outside any cluster mean the same thing");
        assertNull(card.clusterName());
    }

    @Test
    void aStationHiddenFromDiscoveryIsOnNoCardAtAll() {
        var cluster = clusterService.create("Kreisverband Versteckt " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Versteckt " + NAMES.incrementAndGet());

        assertTrue(
                service.publicCards().stream()
                        .noneMatch(c -> c.stationUid().equals(station.uid().toString())),
                "a station that is not on the page is in no group either");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }
}
