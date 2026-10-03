/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clusters many stations are members of, read in one query.
 */
class ClusterByStationRepositoryTest extends RepositoryTestBase {

    @Test
    void eachMemberStationIsGivenItsClusterAndAStationOutsideAnyIsLeftOut() {
        var north = clusterService.create("Kreis Nord " + UUID.randomUUID(), null);
        var south = clusterService.create("Kreis Süd " + UUID.randomUUID(), null);
        var inNorth = clusterService.createStation(north.id(), "Wache Nord " + UUID.randomUUID());
        var inSouth = clusterService.createStation(south.id(), "Wache Süd " + UUID.randomUUID());
        var alone = stationRepo.create("Wache Allein " + UUID.randomUUID());

        var clusters = clusterRepo.findByStations(List.of(inNorth.id(), inSouth.id(), alone.id()));

        assertEquals(Set.of(inNorth.id(), inSouth.id()), clusters.keySet());
        assertEquals(north.id(), clusters.get(inNorth.id()).id());
        assertEquals(south.name(), clusters.get(inSouth.id()).name());
        assertEquals(
                clusterRepo.findByStation(inNorth.id()).orElseThrow().uid(),
                clusters.get(inNorth.id()).uid());
        assertTrue(clusterRepo.findByStations(List.of()).isEmpty());
        stationRepo.delete(alone.id());
    }
}
