/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.entity.RecommendedTag;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterInventoryTagServiceTest extends RepositoryTestBase {

    private static Account account;
    private static Station clusterHome;
    private static Station member;
    private static Station otherHome;
    private static int clusterId;
    private static int otherClusterId;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("clustertagsvc@test.example", "Cluster", "TagService");
        clusterHome = stationRepo.create("ClusterTagSvcHome");
        clusterId = clusterRepo
                .create("ClusterTagSvcVerband", null, clusterHome.id())
                .id();
        otherHome = stationRepo.create("ClusterTagSvcOtherHome");
        otherClusterId = clusterRepo
                .create("ClusterTagSvcAndererVerband", null, otherHome.id())
                .id();
        member = stationRepo.create("ClusterTagSvcStation");
        stationRepo.setCluster(member.id(), clusterId);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(member.id());
        clusterRepo.delete(clusterId);
        clusterRepo.delete(otherClusterId);
        stationRepo.delete(clusterHome.id());
        stationRepo.delete(otherHome.id());
        accountRepo.delete(account.id());
    }

    @Test
    void oneWordIsRecommendedOnceAndNotTwice() {
        var funk = clusterInventoryTagService.create(clusterId, "Funk", "#3694FF", null);
        assertEquals(
                funk.id(),
                clusterInventoryTagService.findById(funk.id()).orElseThrow().id());
        assertEquals(1, clusterInventoryTagService.findByCluster(clusterId).size());

        assertThrows(
                BadRequestResponse.class, () -> clusterInventoryTagService.create(clusterId, " FUNK ", null, null));
        assertThrows(BadRequestResponse.class, () -> clusterInventoryTagService.create(clusterId, "  ", null, null));
        assertThrows(BadRequestResponse.class, () -> clusterInventoryTagService.create(clusterId, null, null, null));

        clusterInventoryTagService.delete(clusterId, funk.id());
    }

    @Test
    void aRecommendationOfAnotherAssociationIsNotThereAtAll() {
        var theirs = clusterInventoryTagService.create(otherClusterId, "Fremd", null, null);
        assertThrows(
                NotFoundResponse.class,
                () -> clusterInventoryTagService.update(clusterId, theirs.id(), "Neu", null, 0, null));
        assertThrows(NotFoundResponse.class, () -> clusterInventoryTagService.delete(clusterId, theirs.id()));
        assertThrows(
                NotFoundResponse.class, () -> clusterInventoryTagService.update(clusterId, -1, "Neu", null, 0, null));
        clusterInventoryTagService.delete(otherClusterId, theirs.id());
    }

    @Test
    void changingARecommendationOntoAnotherIsRefused() {
        var funk = clusterInventoryTagService.create(clusterId, "ChangeFunk", null, null);
        var licht = clusterInventoryTagService.create(clusterId, "ChangeLicht", null, null);

        assertThrows(
                BadRequestResponse.class,
                () -> clusterInventoryTagService.update(clusterId, licht.id(), "changefunk", null, 0, null));

        var changed = clusterInventoryTagService.update(clusterId, licht.id(), " ChangeLicht ", "#00C507", 4, null);
        assertEquals("ChangeLicht", changed.name());
        assertEquals(4, changed.position());

        clusterInventoryTagService.delete(clusterId, funk.id());
        clusterInventoryTagService.delete(clusterId, licht.id());
    }

    @Test
    void aStationSeesWhichRecommendationsItAlreadyUses() {
        clusterInventoryTagService.create(clusterId, "Funk", null, null);
        clusterInventoryTagService.create(clusterId, "Zelt", null, null);
        var own = inventoryTagRepo.create(member.id(), " funk ", null);

        var recommendations = clusterInventoryTagService.recommendationsFor(member.id());
        assertEquals(2, recommendations.size());
        assertTrue(recommendations.stream()
                .filter(tag -> "Funk".equals(tag.name()))
                .findFirst()
                .map(RecommendedTag::adopted)
                .orElseThrow());
        assertFalse(recommendations.stream()
                .filter(tag -> "Zelt".equals(tag.name()))
                .findFirst()
                .map(RecommendedTag::adopted)
                .orElseThrow());

        assertTrue(
                clusterInventoryTagService.recommendationsFor(clusterHome.id()).isEmpty());

        inventoryTagRepo.delete(own.id(), member.id());
        for (var tag : clusterInventoryTagService.findByCluster(clusterId)) {
            clusterInventoryTagService.delete(clusterId, tag.id());
        }
    }
}
