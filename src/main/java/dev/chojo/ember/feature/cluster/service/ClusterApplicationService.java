/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

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
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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
     * @throws ForbiddenResponse  when somebody other than the owner asks
     * @throws BadRequestResponse when the station already belongs to a cluster or is a cluster's own shell
     */
    public ClusterApplication apply(int clusterId, int stationId, int actorMemberId) {
        Station station = requireStation(stationId);
        Cluster cluster = requireCluster(clusterId);

        if (station.ownerMemberId() == null || station.ownerMemberId() != actorMemberId) {
            throw new ForbiddenResponse("Only the station's owner can ask to join a cluster");
        }
        if (station.stationKind() == StationKind.CLUSTER_HOME) {
            throw new BadRequestResponse("A cluster's own station cannot join another cluster");
        }
        if (station.clusterId() != null) {
            throw new BadRequestResponse("This station already belongs to a cluster");
        }
        applicationRepository.findPendingForStation(stationId).ifPresent(pending -> {
            throw new BadRequestResponse("This station already has a request waiting");
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
     * @throws ForbiddenResponse  when somebody other than the owner withdraws
     * @throws BadRequestResponse when it was already decided
     */
    public void withdraw(int applicationId, int actorMemberId) {
        ClusterApplication application = requireApplication(applicationId);
        Station station = requireStation(application.stationId());

        if (station.ownerMemberId() == null || station.ownerMemberId() != actorMemberId) {
            throw new ForbiddenResponse("Only the station's owner can withdraw its request");
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
     * @throws BadRequestResponse when it was already decided, or the station joined a cluster meanwhile
     */
    public void approve(int applicationId, int clusterId, Integer resolvingMemberId) {
        ClusterApplication application = requireApplication(applicationId);
        requireSameCluster(application, clusterId);
        requireOpen(application);

        Station station = requireStation(application.stationId());
        if (station.clusterId() != null) {
            throw new BadRequestResponse("This station has joined a cluster in the meantime");
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
     * @throws BadRequestResponse when it was already decided
     */
    public void deny(int applicationId, int clusterId, String reason, Integer resolvingMemberId) {
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
            throw new BadRequestResponse("This request has already been decided");
        }
    }

    private static void requireSameCluster(ClusterApplication application, int clusterId) {
        if (application.clusterId() != clusterId) {
            throw new NotFoundResponse("No such application");
        }
    }

    private ClusterApplication requireApplication(int id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundResponse("No such application"));
    }

    private Station requireStation(int id) {
        return stationRepository.findById(id).orElseThrow(() -> new NotFoundResponse("No such station"));
    }

    private Cluster requireCluster(int id) {
        return clusterRepository.findById(id).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }
}
