/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.cluster.entity.ClusterApplicationStatus;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterApplicationServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterApplicationService service;

    @BeforeAll
    static void setup() {
        service = new ClusterApplicationService(
                clusterApplicationRepo, clusterRepo, stationRepo, clusterService, new DomainEventBus(Set.of()));
    }

    private int freshCluster() {
        return clusterService
                .create("Kreisverband " + NAMES.incrementAndGet(), "Der Träger")
                .id();
    }

    /** A station with an owner, which is the only person who may ask a cluster for anything. */
    private record OwnedStation(Station station, int ownerMemberId) {}

    private OwnedStation freshOwnedStation() {
        int n = NAMES.incrementAndGet();
        Station station = stationRepo.create("Wache " + n);
        var account = accountRepo.create("apply" + n + "@test.com", "App", "Ly" + n);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationRepo.setOwner(station.id(), member.id());
        return new OwnedStation(stationRepo.findById(station.id()).orElseThrow(), member.id());
    }

    @Test
    void onlyTheOwnerMayAsk() {
        int clusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();
        int stranger = owned.ownerMemberId() + 10_000;

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.apply(clusterId, owned.station().id(), stranger));
        assertEquals(Refusal.CLUSTER_APPLICATION_NOT_BY_STATION_OWNER, refused.refusal());
        assertTrue(service.findPendingForStation(owned.station().id()).isEmpty());
    }

    @Test
    void anApprovedStationBelongsToTheCluster() {
        int clusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        var application = service.apply(clusterId, owned.station().id(), owned.ownerMemberId());
        assertEquals(ClusterApplicationStatus.PENDING, application.status());

        service.approve(application.id(), clusterId, null);

        var station = stationRepo.findById(owned.station().id()).orElseThrow();
        assertEquals(clusterId, station.clusterId());
        assertEquals(
                ClusterApplicationStatus.APPROVED,
                service.findById(application.id()).orElseThrow().status());
    }

    @Test
    void aDeniedStationKeepsItsFreedomAndTheReason() {
        int clusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        var application = service.apply(clusterId, owned.station().id(), owned.ownerMemberId());
        service.deny(application.id(), clusterId, "Zu weit weg", null);

        var closed = service.findById(application.id()).orElseThrow();
        assertEquals(ClusterApplicationStatus.DENIED, closed.status());
        assertEquals("Zu weit weg", closed.denyReason());
        assertNull(stationRepo.findById(owned.station().id()).orElseThrow().clusterId());
    }

    @Test
    void aStationThatChangedItsMindTakesTheRequestBack() {
        int clusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        var application = service.apply(clusterId, owned.station().id(), owned.ownerMemberId());
        service.withdraw(application.id(), owned.ownerMemberId());

        assertEquals(
                ClusterApplicationStatus.WITHDRAWN,
                service.findById(application.id()).orElseThrow().status());
        assertTrue(service.findPendingForStation(owned.station().id()).isEmpty());
    }

    @Test
    void aDecidedRequestCannotBeDecidedTwice() {
        int clusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        var application = service.apply(clusterId, owned.station().id(), owned.ownerMemberId());
        service.approve(application.id(), clusterId, null);

        var refused =
                assertThrows(RefusalResponse.class, () -> service.deny(application.id(), clusterId, "zu spät", null));
        assertEquals(Refusal.CLUSTER_APPLICATION_ALREADY_DECIDED, refused.refusal());
    }

    @Test
    void aStationWithARequestWaitingCannotOpenASecond() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        service.apply(clusterId, owned.station().id(), owned.ownerMemberId());

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.apply(otherClusterId, owned.station().id(), owned.ownerMemberId()));
        assertEquals(Refusal.CLUSTER_APPLICATION_ALREADY_WAITING, refused.refusal());
    }

    @Test
    void aStationThatAlreadyBelongsSomewhereCannotAsk() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        clusterService.joinStation(clusterId, owned.station().id());

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.apply(otherClusterId, owned.station().id(), owned.ownerMemberId()));
        assertEquals(Refusal.CLUSTER_APPLICATION_STATION_ALREADY_JOINED, refused.refusal());
    }

    @Test
    void oneClusterCannotAnswerAnothersPost() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        var application = service.apply(clusterId, owned.station().id(), owned.ownerMemberId());

        var refused =
                assertThrows(RefusalResponse.class, () -> service.approve(application.id(), otherClusterId, null));
        assertEquals(Refusal.CLUSTER_APPLICATION_NOT_HERE, refused.refusal());
    }

    @Test
    void askingAgainAfterARefusalReopensTheSameRow() {
        int clusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        var first = service.apply(clusterId, owned.station().id(), owned.ownerMemberId());
        service.deny(first.id(), clusterId, "Erstmal nicht", null);

        var second = service.apply(clusterId, owned.station().id(), owned.ownerMemberId());
        assertEquals(first.id(), second.id(), "one row per station and cluster, whatever became of it");
        assertEquals(ClusterApplicationStatus.PENDING, second.status());
        assertNull(second.denyReason(), "the old refusal does not follow the new request");
        assertNotNull(second.requestedAt());
    }

    @Test
    void theClusterSeesWhoAskedAndTheStationSeesWhoWasAsked() {
        int clusterId = freshCluster();
        OwnedStation owned = freshOwnedStation();

        service.apply(clusterId, owned.station().id(), owned.ownerMemberId());

        assertEquals(1, service.findByCluster(clusterId).size());
        assertEquals(1, service.findByStation(owned.station().id()).size());
    }
}
