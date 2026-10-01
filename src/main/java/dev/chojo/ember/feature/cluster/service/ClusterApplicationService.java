/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterApplicationResolved;
import dev.chojo.ember.event.events.ClusterApplicationSubmitted;
import dev.chojo.ember.event.events.ClusterApplicationWithdrawn;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterApplication;
import dev.chojo.ember.feature.cluster.entity.ClusterApplicationStatus;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.cluster.repository.ClusterApplicationRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * A standing station asking to join a cluster, and what the cluster answers.
 *
 * <p>The direction is the point. A cluster can create stations of its own and they belong to it from the
 * first moment, but it can never reach out and take one that already exists: somebody at that station has to
 * offer, and only its owner may. Everything here enforces that one way or another.
 */
@Singleton
public class ClusterApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ClusterApplicationService.class);

    private final ClusterApplicationRepository applicationRepository;
    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final ClusterService clusterService;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterApplicationService(
            ClusterApplicationRepository applicationRepository,
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            ClusterService clusterService,
            DomainEventBus eventBus) {
        this.applicationRepository = applicationRepository;
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.clusterService = clusterService;
        this.eventBus = eventBus;
    }

    /**
     * Opens a request for a station to join a cluster.
     *
     * @param clusterId     the cluster being asked
     * @param stationId     the station asking
     * @param actorMemberId the station member doing the asking, who must be the station's owner
     * @return the pending application
     * @throws RefusalResponse when somebody other than the owner asks, or the station already belongs to a cluster
     *                         or is a cluster's own shell
     */
    public ClusterApplication apply(int clusterId, int stationId, int actorMemberId) {
        Station station = requireStation(stationId);
        Cluster cluster = requireCluster(clusterId);

        if (!station.isOwnedBy(actorMemberId)) {
            throw Refusal.CLUSTER_APPLICATION_NOT_BY_STATION_OWNER.raise();
        }
        if (station.stationKind() == StationKind.CLUSTER_HOME) {
            throw Refusal.CLUSTER_APPLICATION_FROM_CLUSTER_HOME.raise();
        }
        if (station.clusterId() != null) {
            throw Refusal.CLUSTER_APPLICATION_STATION_ALREADY_JOINED.raise();
        }
        applicationRepository.findPendingForStation(stationId).ifPresent(pending -> {
            throw Refusal.CLUSTER_APPLICATION_ALREADY_WAITING.raise();
        });

        ClusterApplication application = applicationRepository.open(clusterId, stationId, actorMemberId);
        log.info("Station {} applied to cluster {}", stationId, clusterId);
        eventBus.publish(new ClusterApplicationSubmitted(cluster.id(), stationId, station.name()));
        return application;
    }

    /**
     * Takes a request back before it was decided.
     *
     * @param applicationId the application
     * @param actorMemberId the station member withdrawing, who must be the station's owner
     * @throws RefusalResponse when somebody other than the owner withdraws, or it was already decided
     */
    public void withdraw(int applicationId, int actorMemberId) {
        ClusterApplication application = requireApplication(applicationId);
        Station station = requireStation(application.stationId());

        if (!station.isOwnedBy(actorMemberId)) {
            throw Refusal.CLUSTER_APPLICATION_WITHDRAWN_NOT_BY_STATION_OWNER.raise();
        }
        requireOpen(application);

        applicationRepository.resolve(applicationId, ClusterApplicationStatus.WITHDRAWN, null, null);
        log.info("Station {} withdrew its application to cluster {}", station.id(), application.clusterId());
        eventBus.publish(new ClusterApplicationWithdrawn(station.id(), application.clusterId(), station.name()));
    }

    /**
     * Lets a station in.
     *
     * @param applicationId    the application
     * @param clusterId        the cluster acting, checked against the application so one cluster cannot
     *                         answer another's post
     * @param resolvingMemberId the cluster member deciding
     * @throws RefusalResponse when it was already decided, or the station joined a cluster meanwhile
     */
    public void approve(int applicationId, int clusterId, @Nullable Integer resolvingMemberId) {
        ClusterApplication application = requireApplication(applicationId);
        requireSameCluster(application, clusterId);
        requireOpen(application);

        Station station = requireStation(application.stationId());
        if (station.clusterId() != null) {
            throw Refusal.CLUSTER_APPLICATION_STATION_JOINED_MEANWHILE.raise();
        }

        applicationRepository.resolve(applicationId, ClusterApplicationStatus.APPROVED, null, resolvingMemberId);
        log.info("Cluster {} took on station {}, decided by member {}", clusterId, station.id(), resolvingMemberId);
        clusterService.joinStation(clusterId, station.id());
    }

    /**
     * Refuses a station, with a reason its owner can read.
     *
     * @param applicationId     the application
     * @param clusterId         the cluster acting
     * @param reason            why, in the cluster's own words
     * @param resolvingMemberId the cluster member deciding
     * @throws RefusalResponse when it was already decided
     */
    public void deny(int applicationId, int clusterId, @Nullable String reason, @Nullable Integer resolvingMemberId) {
        ClusterApplication application = requireApplication(applicationId);
        requireSameCluster(application, clusterId);
        requireOpen(application);

        Cluster cluster = requireCluster(clusterId);
        applicationRepository.resolve(applicationId, ClusterApplicationStatus.DENIED, reason, resolvingMemberId);
        log.info("Cluster {} denied station {}", clusterId, application.stationId());
        eventBus.publish(new ClusterApplicationResolved(application.stationId(), cluster.name(), false, reason));
    }

    public List<ClusterApplication> findByCluster(int clusterId) {
        return applicationRepository.findByCluster(clusterId);
    }

    public List<ClusterApplication> findByStation(int stationId) {
        return applicationRepository.findByStation(stationId);
    }

    public Optional<ClusterApplication> findPendingForStation(int stationId) {
        return applicationRepository.findPendingForStation(stationId);
    }

    public Optional<ClusterApplication> findById(int id) {
        return applicationRepository.findById(id);
    }

    private static void requireOpen(ClusterApplication application) {
        if (!application.status().open()) {
            throw Refusal.CLUSTER_APPLICATION_ALREADY_DECIDED.raise();
        }
    }

    private static void requireSameCluster(ClusterApplication application, int clusterId) {
        if (application.clusterId() != clusterId) {
            throw Refusal.CLUSTER_APPLICATION_NOT_HERE.raise();
        }
    }

    private ClusterApplication requireApplication(int id) {
        return applicationRepository.findById(id).orElseThrow(Refusal.CLUSTER_APPLICATION_NOT_HERE::raise);
    }

    private Station requireStation(int id) {
        return stationRepository.findById(id).orElseThrow(Refusal.CLUSTER_APPLICATION_STATION_NOT_HERE::raise);
    }

    private Cluster requireCluster(int id) {
        return clusterRepository.findById(id).orElseThrow(Refusal.CLUSTER_APPLICATION_CLUSTER_NOT_HERE::raise);
    }
}
