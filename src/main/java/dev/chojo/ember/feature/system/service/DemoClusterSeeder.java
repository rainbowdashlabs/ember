/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterApplicationRepository;
import dev.chojo.ember.feature.cluster.service.ClusterInventoryService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Seeds the body above the demo station: a district association with its home station, the demo
 * administrator acting for it, and the station itself placed underneath.
 *
 * <p>It runs in the modules band because it needs the station and its administrator and nothing else. The
 * cluster's own inventory and the movements between it and its stations arrive with the phase that builds
 * them; what this gives that phase is somewhere to hang them.
 *
 * <p>Both ways into a cluster are shown, because they behave differently and the difference is the point: a
 * station the cluster made itself, and a standing station whose request is still waiting to be answered.
 */
@Singleton
public class DemoClusterSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoClusterSeeder.class);

    private final ClusterService clusterService;
    private final ClusterMemberService memberService;
    private final ClusterInventoryService inventoryService;
    private final ClusterApplicationRepository applicationRepository;
    private final StationRepository stationRepository;

    @Inject
    public DemoClusterSeeder(
            ClusterService clusterService,
            ClusterMemberService memberService,
            ClusterInventoryService inventoryService,
            ClusterApplicationRepository applicationRepository,
            StationRepository stationRepository) {
        this.clusterService = clusterService;
        this.memberService = memberService;
        this.inventoryService = inventoryService;
        this.applicationRepository = applicationRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seed(DemoSeederContext context) {
        Cluster cluster = clusterService.create(
                "Kreisverband Musterstadt", "Der Träger, dem die Wache und ihre Nachbarn angehören");

        // The station the rest of the demo is about now answers to somebody
        clusterService.joinStation(cluster.id(), context.station().id());

        // A station the cluster made itself, which belonged to it from its first moment
        clusterService.createStation(cluster.id(), "Löschzug Nord");

        // And one standing outside that has asked to come in, so the applications screen has something to
        // decide. It has no owner of its own, which is why the row is written rather than applied for.
        Station neighbour = stationRepository.create("Feuerwehr Nachbardorf");
        applicationRepository.open(cluster.id(), neighbour.id(), null);

        // The demo administrator wears both hats, which is the case worth being able to click through
        clusterService.addMember(cluster.id(), context.adminAccount().id(), ClusterUserType.CLUSTER_ADMIN);

        // A group with something in it, so the third way to hold a permission is visible rather than theoretical
        var group = memberService.createGroup(cluster.id(), "Gerätewarte");
        memberService.setGroupPermissions(cluster.id(), group.id(), Set.of(ClusterPermission.CLUSTER_INVENTORY_EDIT));

        // The cluster keeps its gear here, with a chain of its own, so its steps actually appear in a
        // movement rather than the stations behaving as if nothing were above them
        inventoryService.setUsesInventory(cluster.id(), true);
        inventoryService.createFlow(cluster.id(), "Tausch über den Kreisverband", MovementPurpose.EXCHANGE);
        inventoryService.createFlow(cluster.id(), "Rückgabe an den Kreisverband", MovementPurpose.RETURN);

        log.info(
                "Demo: Created cluster {} with home station {} over station {}",
                cluster.id(),
                cluster.homeStationId(),
                context.station().id());
    }
}
