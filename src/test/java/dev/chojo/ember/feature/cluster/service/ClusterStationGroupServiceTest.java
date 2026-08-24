/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How an association files its stations, and what the filing may not do.
 */
class ClusterStationGroupServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static int freshCluster() {
        return clusterService
                .create("Kreisverband Wachgruppen " + NAMES.incrementAndGet(), null)
                .id();
    }

    /**
     * Setting the stations replaces rather than adds, because the panel hands back the whole set it drew.
     */
    @Test
    void aGroupIsFiledRenamedAndFilledWithStations() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Gruppe " + NAMES.incrementAndGet());

        var group = clusterStationGroupService.create(clusterId, "  Nordkreis  ");
        assertEquals("Nordkreis", group.name(), "a name is filed without the space somebody typed around it");

        clusterStationGroupService.rename(clusterId, group.id(), "Nordkreis und Küste");
        assertEquals(
                "Nordkreis und Küste",
                clusterStationGroupService.findByCluster(clusterId).getFirst().name());

        clusterStationGroupService.setStations(clusterId, group.id(), List.of(station.uid()));
        assertEquals(
                station.id(),
                clusterStationGroupService
                        .findStations(clusterId, group.id())
                        .getFirst()
                        .id());
        assertEquals(List.of(group.id()), clusterStationGroupRepo.findGroupIdsOfStation(station.id()));

        clusterStationGroupService.setStations(clusterId, group.id(), List.of());
        assertTrue(
                clusterStationGroupService.findStations(clusterId, group.id()).isEmpty());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
        clusterService.delete(clusterId);
    }

    @Test
    void aFilingReachesOnlyTheAssociationsOwnStationsAndNeverItsOwnStore() {
        int clusterId = freshCluster();
        int otherId = freshCluster();
        var cluster = clusterRepo.findById(clusterId).orElseThrow();
        var home = stationRepo.findById(cluster.homeStationId()).orElseThrow();
        var foreign = clusterService.createStation(otherId, "Wache Fremd " + NAMES.incrementAndGet());
        var group = clusterStationGroupService.create(clusterId, "Nordkreis " + NAMES.incrementAndGet());

        assertThrows(
                BadRequestResponse.class,
                () -> clusterStationGroupService.setStations(clusterId, group.id(), List.of(foreign.uid())),
                "one association cannot file another's stations");
        assertThrows(
                BadRequestResponse.class,
                () -> clusterStationGroupService.setStations(clusterId, group.id(), List.of(home.uid())),
                "and its own store is not one of its stations");
        assertThrows(
                NotFoundResponse.class,
                () -> clusterStationGroupService.rename(otherId, group.id(), "Fremd"),
                "nor rename a group that is not its own");

        clusterService.releaseStation(otherId, foreign.id());
        stationRepo.delete(foreign.id());
        clusterService.delete(otherId);
        clusterService.delete(clusterId);
    }

    @Test
    void twoGroupsOfOneAssociationCannotShareAName() {
        int clusterId = freshCluster();
        clusterStationGroupService.create(clusterId, "Nordkreis");

        var refused =
                assertThrows(BadRequestResponse.class, () -> clusterStationGroupService.create(clusterId, "nordkreis"));
        assertTrue(refused.getMessage().contains("already files"));

        clusterService.delete(clusterId);
    }

    /**
     * Deleting a filing that questions are pointed at would silently delete the questions and every answer to
     * them, so it is refused with what is in the way.
     */
    @Test
    void aGroupQuestionsAreAskedOfCannotBeRemoved() {
        int clusterId = freshCluster();
        var group = clusterStationGroupService.create(clusterId, "Atemschutz " + NAMES.incrementAndGet());
        var field = clusterProfileFieldService.create(
                clusterId,
                "Atemschutztauglich",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                group.id());

        var refused =
                assertThrows(BadRequestResponse.class, () -> clusterStationGroupService.delete(clusterId, group.id()));
        assertTrue(refused.getMessage().contains("1 question"));

        clusterProfileFieldService.delete(clusterId, field.id());
        clusterStationGroupService.delete(clusterId, group.id());
        assertTrue(clusterStationGroupService.findByCluster(clusterId).isEmpty());

        clusterService.delete(clusterId);
    }

    /**
     * A station that has left the association is in none of its filings, and the filing does not follow it.
     */
    @Test
    void aReleasedStationIsInNoFilingAnyMore() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Entlassen " + NAMES.incrementAndGet());
        var group = clusterStationGroupService.create(clusterId, "Nordkreis " + NAMES.incrementAndGet());
        clusterStationGroupService.setStations(clusterId, group.id(), List.of(station.uid()));

        clusterService.releaseStation(clusterId, station.id());

        assertTrue(clusterStationGroupRepo.findGroupIdsOfStation(station.id()).isEmpty());
        assertTrue(
                clusterStationGroupService.findStations(clusterId, group.id()).isEmpty());

        stationRepo.delete(station.id());
        clusterService.delete(clusterId);
    }
}
