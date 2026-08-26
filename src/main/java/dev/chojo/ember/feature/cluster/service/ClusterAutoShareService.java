/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Passes on what is written at a station a cluster owns.
 *
 * <p>A cluster keeps its knowledge base, its news and its calendar on a station of its own, and writes them
 * with the ordinary station screens. What makes that writing the cluster's rather than one station's is that
 * every station under the cluster reads it: an article nobody under the cluster can see is an article the
 * cluster did not write. So anything written there is shared with the cluster's stations as it is made,
 * without anybody having to remember the second step.
 *
 * <p>Every method is a no-op for an ordinary station, which is what all but a handful of stations are.
 *
 * <p>News and appointments are passed on from the handlers of the events their creation already publishes,
 * rather than from here: the services that share them are built on the services that make them, so reaching
 * for one from the other would tie the two into a knot. The knowledge base publishes no such event, so its
 * two calls sit in the service that writes it.
 */
@Singleton
public class ClusterAutoShareService {
    private static final Logger log = LoggerFactory.getLogger(ClusterAutoShareService.class);

    private final ClusterRepository clusterRepository;
    private final FederationRepository federationRepository;

    @Inject
    public ClusterAutoShareService(ClusterRepository clusterRepository, FederationRepository federationRepository) {
        this.clusterRepository = clusterRepository;
        this.federationRepository = federationRepository;
    }

    /**
     * Whether this station is one a cluster keeps its own things on.
     *
     * @param stationId the station something was written at
     * @return the cluster it belongs to, or empty for an ordinary station
     */
    public Optional<Cluster> owningCluster(int stationId) {
        return clusterRepository.findByHomeStation(stationId);
    }

    /**
     * Shares a knowledge article written at a cluster's own station with the cluster's stations.
     *
     * @param stationId the station it was written at
     * @param fileId    the article
     */
    public void shareKbFile(int stationId, int fileId) {
        owningCluster(stationId).ifPresent(cluster -> {
            federationRepository.createKbShare(stationId, fileId, null, ShareScope.ALL_PARTNERS);
            log.info("Cluster {} wrote article {} and it goes to its stations", cluster.id(), fileId);
        });
    }

    /**
     * Shares a knowledge folder made at a cluster's own station with the cluster's stations.
     *
     * @param stationId the station it was made at
     * @param folderId  the folder
     */
    public void shareKbFolder(int stationId, int folderId) {
        owningCluster(stationId).ifPresent(cluster -> {
            federationRepository.createKbShare(stationId, null, folderId, ShareScope.ALL_PARTNERS);
            log.info("Cluster {} made folder {} and it goes to its stations", cluster.id(), folderId);
        });
    }
}
