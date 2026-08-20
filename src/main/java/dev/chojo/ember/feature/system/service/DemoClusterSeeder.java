/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seeds the body above the demo station: a district association with its home station, the demo
 * administrator acting for it, and the station itself placed underneath.
 *
 * <p>It runs in the modules band because it needs the station and its administrator and nothing else. The
 * cluster's own inventory and the movements between it and its stations arrive with the phase that builds
 * them; what this gives that phase is somewhere to hang them.
 */
@Singleton
public class DemoClusterSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoClusterSeeder.class);

    private final ClusterService clusterService;
    private final ClusterRepository clusterRepository;

    @Inject
    public DemoClusterSeeder(ClusterService clusterService, ClusterRepository clusterRepository) {
        this.clusterService = clusterService;
        this.clusterRepository = clusterRepository;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seed(DemoSeederContext context) {
        var cluster = clusterService.create(
                "Kreisverband Musterstadt", "Der Träger, dem die Wache und ihre Nachbarn angehören");

        // The station the rest of the demo is about now answers to somebody
        clusterRepository.setStationCluster(context.station().id(), cluster.id());

        // The demo administrator wears both hats, which is the case worth being able to click through
        clusterService.addMember(cluster.id(), context.adminAccount().id(), ClusterUserType.CLUSTER_ADMIN);

        log.info(
                "Demo: Created cluster {} with home station {} over station {}",
                cluster.id(),
                cluster.homeStationId(),
                context.station().id());
    }
}
