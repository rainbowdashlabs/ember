/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What somebody looking after every station of a cluster may and may not do.
 *
 * <p>The two refusals are the point of the class, so most of this is about them.
 */
class ClusterMemberManagementServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterMemberManagementService service;

    @BeforeAll
    static void setup() {
        service = new ClusterMemberManagementService(stationMemberRepo, stationRepo);
    }

    private int freshCluster() {
        return clusterService
                .create("Kreisverband Leute " + NAMES.incrementAndGet(), null)
                .id();
    }

    private Account freshAccount() {
        int n = NAMES.incrementAndGet();
        return accountRepo.create("clustermanage" + n + "@test.com", "Ver", "Waltung" + n);
    }

    /** A station of the cluster with one ordinary member in it. */
    private record Peopled(Station station, StationMember member, Account account) {}

    private Peopled stationWithMember(int clusterId) {
        var station = clusterService.createStation(clusterId, "Wache Leute " + NAMES.incrementAndGet());
        var account = freshAccount();
        var member = stationMemberRepo.create(station.id(), account.id());
        return new Peopled(station, member, account);
    }

    @Test
    void everybodyAtEveryStationOfTheClusterIsFound() {
        int clusterId = freshCluster();
        var first = stationWithMember(clusterId);
        var second = stationWithMember(clusterId);

        var page = service.search(clusterId, null, null, null, false, 0, 50);

        assertEquals(2, page.total());
        assertTrue(page.members().stream()
                .anyMatch(row -> row.id() == first.member().id()));
        assertTrue(page.members().stream()
                .anyMatch(row -> row.id() == second.member().id()));
        assertTrue(
                page.members().stream().allMatch(row -> row.stationName().startsWith("Wache Leute")),
                "each row says which station the person is at");
    }

    @Test
    void aSearchNarrowsToOneStation() {
        int clusterId = freshCluster();
        var first = stationWithMember(clusterId);
        stationWithMember(clusterId);

        var page = service.search(clusterId, null, first.station().id(), null, false, 0, 50);

        assertEquals(1, page.total());
        assertEquals(first.member().id(), page.members().getFirst().id());
    }

    @Test
    void aSearchNarrowsByNameAndByType() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);

        assertEquals(
                1,
                service.search(clusterId, peopled.account().firstName(), null, null, false, 0, 50)
                        .total());
        assertEquals(
                0,
                service.search(clusterId, "niemand-mit-diesem-namen", null, null, false, 0, 50)
                        .total());
        assertEquals(
                1,
                service.search(clusterId, null, null, peopled.member().userType(), false, 0, 50)
                        .total());
    }

    @Test
    void peopleWhoHaveLeftAreOutOfTheWayUnlessAskedFor() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        stationMemberRepo.setFormer(peopled.member().id(), true);

        assertEquals(
                0, service.search(clusterId, null, null, null, false, 0, 50).total());
        assertEquals(1, service.search(clusterId, null, null, null, true, 0, 50).total());
    }

    @Test
    void aClusterSeesNobodyFromAnotherClustersStations() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        stationWithMember(otherClusterId);

        assertEquals(
                0, service.search(clusterId, null, null, null, false, 0, 50).total());
    }

    @Test
    void aManagerCannotEditTheirOwnMembership() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int ownAccountId = peopled.account().id();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.setUserType(clusterId, peopled.member().id(), StationUserType.MANAGER, ownAccountId));
        assertThrows(
                ForbiddenResponse.class,
                () -> service.setPermissions(
                        clusterId,
                        peopled.member().id(),
                        Set.of(StationPermission.STATION_ADMINISTRATOR),
                        ownAccountId));
        assertThrows(
                ForbiddenResponse.class,
                () -> service.archive(clusterId, peopled.member().id(), ownAccountId));
    }

    @Test
    void aManagerCannotEditAStationsOwner() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        stationRepo.setOwner(peopled.station().id(), peopled.member().id());
        int strangerAccountId = freshAccount().id();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.setUserType(
                        clusterId, peopled.member().id(), StationUserType.MANAGER, strangerAccountId));
        assertThrows(
                ForbiddenResponse.class,
                () -> service.archive(clusterId, peopled.member().id(), strangerAccountId));
    }

    @Test
    void anybodyElseCanBeEditedWithNoCeiling() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int strangerAccountId = freshAccount().id();

        service.setUserType(clusterId, peopled.member().id(), StationUserType.MANAGER, strangerAccountId);
        assertEquals(
                StationUserType.MANAGER,
                stationMemberRepo.findById(peopled.member().id()).orElseThrow().userType());

        // Up to and including the top of the station's own ladder, which is deliberate
        service.setPermissions(
                clusterId, peopled.member().id(), Set.of(StationPermission.STATION_ADMINISTRATOR), strangerAccountId);

        service.archive(clusterId, peopled.member().id(), strangerAccountId);
        assertTrue(
                stationMemberRepo.findById(peopled.member().id()).orElseThrow().former());
    }

    @Test
    void aMemberAtSomebodyElsesStationIsNotFoundAtAll() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var elsewhere = stationWithMember(otherClusterId);
        int strangerAccountId = freshAccount().id();

        assertThrows(
                NotFoundResponse.class,
                () -> service.setUserType(
                        clusterId, elsewhere.member().id(), StationUserType.MANAGER, strangerAccountId));
    }

    @Test
    void theStationsAManagerMayActInAreTheClustersOwn() {
        var cluster = clusterService.create("Kreisverband Reichweite " + NAMES.incrementAndGet(), null);
        var peopled = stationWithMember(cluster.id());

        var stations = service.reachableStations(cluster.id());

        assertEquals(1, stations.size());
        assertEquals(peopled.station().id(), stations.getFirst().id());
        assertFalse(
                stations.stream().anyMatch(station -> station.id() == cluster.homeStationId()),
                "the cluster's own shell is not one of them");
    }
}
