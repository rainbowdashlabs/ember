/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pairs a cluster makes, and what its stations may and may not do to them.
 */
class ClusterFederationTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static FederationRepository federationRepository;
    private static FederationService service;

    @BeforeAll
    static void setup() {
        federationRepository = new FederationRepository();
        service = new FederationService(federationRepository, stationRepo, new Api());
    }

    private Station freshStation() {
        return stationRepo.create("Wache Föderiert " + NAMES.incrementAndGet());
    }

    /** Whether a row exists from one station to another, in that direction. */
    private boolean paired(int fromStationId, Station to) {
        return federationRepository.findPartners(fromStationId).stream()
                .anyMatch(p -> p.partnerStationId().equals(to.uid()));
    }

    private FederationPartner pair(int fromStationId, UUID toUid) {
        return federationRepository.findPartners(fromStationId).stream()
                .filter(p -> p.partnerStationId().equals(toUid))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void joiningPairsTheStationWithTheClustersOwnStationBothWays() {
        var home = freshStation();
        var joining = freshStation();

        service.createClusterFederation(home.id(), joining.id(), List.of(), true);

        assertTrue(paired(home.id(), joining), "the cluster reaches the station");
        assertTrue(paired(joining.id(), home), "and the station reaches the cluster");

        var fromHome = pair(home.id(), joining.uid());
        assertTrue(fromHome.clusterManaged());
        assertTrue(fromHome.clusterHome(), "this is the pair the cluster's content travels on");
        assertEquals(FederationPartner.FederationStatus.ACTIVE, fromHome.status(), "no invite to accept");
    }

    @Test
    void everyCapabilityIsOnInBothDirections() {
        var home = freshStation();
        var joining = freshStation();

        service.createClusterFederation(home.id(), joining.id(), List.of(), true);

        var capabilities = federationRepository.findCapabilities(
                pair(home.id(), joining.uid()).id());
        assertEquals(CapabilityType.values().length * 2, capabilities.size(), "written against the enum, not a list");
        assertTrue(capabilities.stream().allMatch(c -> c.enabled()));
    }

    @Test
    void theMeshIsMadeOnlyWhenTheClusterAskedForIt() {
        var home = freshStation();
        var sibling = freshStation();
        var joining = freshStation();

        service.createClusterFederation(home.id(), joining.id(), List.of(sibling.id()), false);
        assertFalse(paired(joining.id(), sibling), "with the setting off the stations stay strangers");

        service.backfillClusterMesh(List.of(sibling.id(), joining.id()));
        assertTrue(paired(joining.id(), sibling), "turning it on fills in what was missing");
        assertTrue(paired(sibling.id(), joining));
        assertFalse(pair(joining.id(), sibling.uid()).clusterHome(), "a mesh pair carries no cluster content");
    }

    @Test
    void backfillingTwiceMakesNothingTwice() {
        var first = freshStation();
        var second = freshStation();

        service.backfillClusterMesh(List.of(first.id(), second.id()));
        int after = federationRepository.findPartners(first.id()).size();
        service.backfillClusterMesh(List.of(first.id(), second.id()));

        assertEquals(after, federationRepository.findPartners(first.id()).size());
    }

    @Test
    void theStationsCannotTakeAClusterPairApartThemselves() {
        var home = freshStation();
        var sibling = freshStation();
        var joining = freshStation();
        service.createClusterFederation(home.id(), joining.id(), List.of(sibling.id()), true);

        int homePairId = pair(joining.id(), home.uid()).id();
        int meshPairId = pair(joining.id(), sibling.uid()).id();

        assertThrows(BadRequestResponse.class, () -> service.endFederation(homePairId));
        assertThrows(BadRequestResponse.class, () -> service.endFederation(meshPairId));
        assertThrows(BadRequestResponse.class, () -> service.suspendPartner(homePairId), "content must keep arriving");

        // A mesh pair is a matter between the two stations, so pausing one is theirs to do
        assertTrue(service.suspendPartner(meshPairId));
    }

    @Test
    void releasingTakesEveryPairTheClusterMadeInBothDirections() {
        var home = freshStation();
        var sibling = freshStation();
        var joining = freshStation();
        service.createClusterFederation(home.id(), joining.id(), List.of(sibling.id()), true);

        service.removeClusterFederation(joining.id());

        assertFalse(paired(joining.id(), home));
        assertFalse(paired(home.id(), joining));
        assertFalse(paired(joining.id(), sibling));
        assertFalse(paired(sibling.id(), joining), "the far side of the mesh goes too");
    }

    @Test
    void aPairTheStationsMadeThemselvesSurvivesTheRelease() {
        var home = freshStation();
        var friend = freshStation();
        var joining = freshStation();
        service.createClusterFederation(home.id(), joining.id(), List.of(), true);
        federationRepository.createPartner(joining.id(), friend.uid(), "invite", "key", null);

        service.removeClusterFederation(joining.id());

        assertTrue(paired(joining.id(), friend), "what the station arranged itself is its own");
    }
}
