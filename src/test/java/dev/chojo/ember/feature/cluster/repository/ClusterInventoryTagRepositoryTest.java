/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterInventoryTagRepositoryTest extends RepositoryTestBase {

    private static Account account;
    private static Station clusterHome;
    private static Station inGroup;
    private static Station outOfGroup;
    private static int clusterId;
    private static int groupId;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("clustertag@test.example", "Cluster", "Tagger");
        clusterHome = stationRepo.create("ClusterTagHome");
        clusterId =
                clusterRepo.create("ClusterTagVerband", null, clusterHome.id()).id();
        inGroup = stationRepo.create("ClusterTagStationIn");
        outOfGroup = stationRepo.create("ClusterTagStationOut");
        stationRepo.setCluster(inGroup.id(), clusterId);
        stationRepo.setCluster(outOfGroup.id(), clusterId);
        groupId = clusterStationGroupRepo.create(clusterId, "Nordgruppe").id();
        clusterStationGroupRepo.setStations(groupId, List.of(inGroup.id()));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(inGroup.id());
        stationRepo.delete(outOfGroup.id());
        clusterRepo.delete(clusterId);
        stationRepo.delete(clusterHome.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aWordReachesEveryStationUnlessItNamesAGroup() {
        var forAll = clusterInventoryTagRepo.create(clusterId, "Funk", "#3694FF", null);
        var forGroup = clusterInventoryTagRepo.create(clusterId, "Nordfunk", null, groupId);

        assertEquals(2, clusterInventoryTagRepo.findByCluster(clusterId).size());
        assertEquals(2, clusterInventoryTagRepo.findForStation(inGroup.id()).size());
        assertEquals(1, clusterInventoryTagRepo.findForStation(outOfGroup.id()).size());
        assertTrue(clusterInventoryTagRepo.findForStation(clusterHome.id()).isEmpty());

        assertEquals("funk", forAll.canonicalName());
        assertTrue(clusterInventoryTagRepo.findById(forAll.id()).isPresent());
        assertEquals(
                forAll.id(),
                clusterInventoryTagRepo
                        .findByName(clusterId, null, " FUNK ")
                        .orElseThrow()
                        .id());
        assertTrue(
                clusterInventoryTagRepo.findByName(clusterId, groupId, "Funk").isEmpty());
        assertEquals(
                forGroup.id(),
                clusterInventoryTagRepo
                        .findByName(clusterId, groupId, "nordfunk")
                        .orElseThrow()
                        .id());

        assertTrue(clusterInventoryTagRepo.update(forGroup.id(), "Nordfunkgerät", "#FF6421", 7, null));
        var changed = clusterInventoryTagRepo.findById(forGroup.id()).orElseThrow();
        assertEquals("nordfunkgerät", changed.canonicalName());
        assertEquals(7, changed.position());
        assertNull(changed.stationGroupId());

        assertTrue(clusterInventoryTagRepo.delete(forGroup.id(), clusterId));
        assertFalse(clusterInventoryTagRepo.delete(forGroup.id(), clusterId));
        assertFalse(clusterInventoryTagRepo.update(forGroup.id(), "x", null, 0, null));
        assertTrue(clusterInventoryTagRepo.delete(forAll.id(), clusterId));
    }
}
