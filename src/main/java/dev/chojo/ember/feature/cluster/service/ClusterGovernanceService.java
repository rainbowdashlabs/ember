/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterModuleDenied;
import dev.chojo.ember.event.events.ClusterQuotaChanged;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.entity.ClusterStationQuota;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageQuotaRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The part of a cluster that reaches into its stations.
 *
 * <p>Kept apart from {@link ClusterService} because it is the only part that writes into station rows, and
 * that is worth being able to see at a glance. Three different kinds of reach live here, and they are not the
 * same kind of thing:
 *
 * <ul>
 *   <li>a denied module is a hard no, which a station cannot argue with,
 *   <li>a look-and-feel setting is a starting point unless the cluster marks it locked,
 *   <li>a quota is arithmetic: the instance grants the cluster a pool and the cluster hands out portions.
 * </ul>
 */
@Singleton
public class ClusterGovernanceService {
    private static final Logger log = LoggerFactory.getLogger(ClusterGovernanceService.class);

    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final ClusterStorageConfigRepository storageConfigRepository;
    private final ClusterStorageQuotaRepository quotaRepository;
    private final StorageBackendResolver backendResolver;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterGovernanceService(
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            ClusterStorageConfigRepository storageConfigRepository,
            ClusterStorageQuotaRepository quotaRepository,
            StorageBackendResolver backendResolver,
            DomainEventBus eventBus) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.storageConfigRepository = storageConfigRepository;
        this.quotaRepository = quotaRepository;
        this.backendResolver = backendResolver;
        this.eventBus = eventBus;
    }

    // -- Modules --

    public Set<StationModule> findDeniedModules(int clusterId) {
        return clusterRepository.findDeniedModules(clusterId);
    }

    /**
     * Sets which modules the cluster switches off for every station under it.
     *
     * <p>Nothing is deleted. A denied module stops being reachable and everything already in it stays where
     * it is, ready to reappear if the denial is lifted or the station released. The stations that were
     * actually using one are told, because a page disappearing without explanation is the kind of thing
     * people report as a fault.
     *
     * @param clusterId the cluster
     * @param modules   the modules it now denies
     */
    public void setDeniedModules(int clusterId, Set<StationModule> modules) {
        Cluster cluster = requireCluster(clusterId);
        Set<StationModule> before = clusterRepository.findDeniedModules(clusterId);
        Set<StationModule> newlyDenied = EnumSet.noneOf(StationModule.class);
        newlyDenied.addAll(modules);
        newlyDenied.removeAll(before);

        clusterRepository.setDeniedModules(clusterId, modules);
        log.info("Cluster {} now denies {}", clusterId, modules);

        for (StationModule module : newlyDenied) {
            for (Station station : stationRepository.findByCluster(clusterId)) {
                // Only the stations that had it switched on lose anything they can see
                if (stationRepository.findDisabledModules(station.id()).contains(module)) continue;
                eventBus.publish(new ClusterModuleDenied(station.id(), cluster.name(), module));
            }
        }
    }

    // -- Look and feel --

    /**
     * Sets the look the cluster hands its stations, and pushes it out.
     *
     * <p>An unlocked setting is a starting point: it is written to every member station now and the station
     * may change it afterwards. A locked one is written too, and the station's own control for it is
     * read-only until the cluster unlocks it or lets the station go.
     *
     * <p>Whether members may pick their own theme stays with the station either way. That is a question about
     * the people at that station, not about how the cluster wants to look.
     *
     * @param clusterId         the cluster
     * @param defaultTheme      the colour theme, or {@code null} for no opinion
     * @param customThemeColors the colour set, or {@code null}
     * @param defaultFeel       the interface feel, or {@code null} for no opinion
     * @param themeLocked       whether the station may change the theme
     * @param colorsLocked      whether the station may change the colours
     * @param feelLocked        whether the station may change the feel
     * @param logoLocked        whether the station may change its logo
     */
    public void setLookAndFeel(
            int clusterId,
            String defaultTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            boolean themeLocked,
            boolean colorsLocked,
            boolean feelLocked,
            boolean logoLocked) {
        requireCluster(clusterId);
        clusterRepository.setLookAndFeel(
                clusterId,
                defaultTheme,
                customThemeColors,
                defaultFeel,
                themeLocked,
                colorsLocked,
                feelLocked,
                logoLocked);

        for (Station station : stationRepository.findByCluster(clusterId)) {
            applyLookTo(clusterRepository.findById(clusterId).orElseThrow(), station);
        }
        log.info("Cluster {} pushed its look to its stations", clusterId);
    }

    /**
     * Writes the cluster's look onto one station, which is what a station joining needs.
     *
     * @param cluster the cluster
     * @param station the station receiving it
     */
    public void applyLookTo(Cluster cluster, Station station) {
        stationRepository.updateThemeSettings(
                station.id(),
                cluster.defaultTheme() != null ? cluster.defaultTheme() : station.defaultTheme(),
                station.allowUserTheme(),
                cluster.customThemeColors() != null ? cluster.customThemeColors() : station.customThemeColors(),
                cluster.defaultFeel() != null ? cluster.defaultFeel() : station.defaultFeel(),
                station.allowUserFeel());
    }

    // -- Storage --

    /**
     * Hands a station a share of the cluster's pool.
     *
     * <p>The pool is the whole of it: the sum of what every station has been promised may not go past it. The
     * cluster's own store is one of those stations, because the files a cluster keeps are kept there and are
     * no more free than anybody else's. A cluster without a pool is one the instance put no cap on, and then
     * there is nothing to check.
     *
     * <p>The number is written where the cluster's own numbers live rather than into the station's, so an
     * instance administrator setting something for a station cannot silently replace what the cluster
     * promised, and the pool adds up what was actually promised.
     *
     * @param clusterId the cluster
     * @param stationId the station receiving the quota
     * @param quotaBytes how much it may use, or {@code null} to hand it back to the cluster's own defaults
     * @throws BadRequestResponse when the cluster would be handing out more than it has
     */
    public void setStationQuota(int clusterId, int stationId, Long quotaBytes) {
        Cluster cluster = requireCluster(clusterId);
        Station station =
                stationRepository.findById(stationId).orElseThrow(() -> new NotFoundResponse("No such station"));
        boolean ownStore = station.id() == cluster.homeStationId();
        if (!ownStore && (station.clusterId() == null || station.clusterId() != clusterId)) {
            throw new BadRequestResponse("That station does not belong to this cluster");
        }

        if (cluster.storagePoolBytes() != null && quotaBytes != null) {
            long othersTotal = quotaRepository.sumGrantedTotals(clusterId, stationId);
            if (othersTotal + quotaBytes > cluster.storagePoolBytes()) {
                throw new BadRequestResponse(
                        "That is more than the cluster has left. Its pool is %d bytes and %d are already handed out."
                                .formatted(cluster.storagePoolBytes(), othersTotal));
            }
        }

        if (quotaBytes == null) {
            quotaRepository.deleteGrant(stationId);
        } else {
            var existing = quotaRepository.findGrant(stationId);
            quotaRepository.setGrant(new ClusterStationQuota(
                    stationId,
                    clusterId,
                    quotaBytes,
                    existing.map(ClusterStationQuota::quotaKbBytes).orElse(null),
                    existing.map(ClusterStationQuota::quotaBoardBytes).orElse(null),
                    existing.map(ClusterStationQuota::quotaImagesBytes).orElse(null),
                    existing.map(ClusterStationQuota::quotaPagesBytes).orElse(null),
                    existing.map(ClusterStationQuota::perFileBytes).orElse(null),
                    existing.map(ClusterStationQuota::perImageBytes).orElse(null),
                    // Setting a total by hand takes the station off whatever tier it was on, because the
                    // numbers no longer come from there
                    null));
        }
        eventBus.publish(new ClusterQuotaChanged(stationId, cluster.name(), quotaBytes));
        log.info("Cluster {} gave station {} a quota of {}", clusterId, stationId, quotaBytes);
    }

    /**
     * The pool the instance grants the cluster.
     *
     * @param clusterId the cluster
     * @param poolBytes how much it may hand out in total, or {@code null} for no cap
     */
    public void setStoragePool(int clusterId, Long poolBytes) {
        requireCluster(clusterId);
        clusterRepository.setStoragePool(clusterId, poolBytes);
    }

    public Optional<StationStorageBackendConfig> findStorageBackend(int clusterId) {
        return storageConfigRepository.findOne(clusterId).map(ClusterStorageConfigRepository.Row::config);
    }

    /**
     * Points the cluster's stations at a backend of the cluster's own.
     *
     * <p>Every member station's resolved backend may have just moved, and the resolver caches that per
     * station, so all of them are dropped from the cache rather than waiting for it to expire on its own.
     *
     * @param clusterId the cluster
     * @param config    the backend, with its credentials already encrypted, or {@code null} to clear it
     */
    public void setStorageBackend(int clusterId, StationStorageBackendConfig config) {
        requireCluster(clusterId);
        if (config == null) {
            storageConfigRepository.delete(clusterId);
        } else {
            storageConfigRepository.upsert(clusterId, config);
        }
        backendResolver.invalidateStations(clusterRepository.findStationIds(clusterId));
        log.info("Cluster {} storage backend set to {}", clusterId, config != null ? config.type() : "the default");
    }

    /**
     * What each member station has been given and what that leaves.
     *
     * @param clusterId the cluster
     * @return one row per station, plus the pool it all comes out of
     */
    public PoolUsage findPoolUsage(int clusterId) {
        Cluster cluster = requireCluster(clusterId);
        List<StationQuota> stations = quotaRepository.findStationsWithGrants(clusterId).stream()
                .map(row -> new StationQuota(row.uid(), row.name(), row.quotaBytes()))
                .toList();
        long handedOut = stations.stream()
                .map(StationQuota::quotaBytes)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        return new PoolUsage(cluster.storagePoolBytes(), handedOut, stations);
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }

    /**
     * @param quotaBytes what the station may use, or {@code null} when it falls back to the instance default
     */
    public record StationQuota(UUID stationUid, String stationName, Long quotaBytes) {}

    /**
     * @param poolBytes  the whole the cluster may hand out, or {@code null} when the instance set no cap
     * @param handedOut  the sum of what its stations have been given
     */
    public record PoolUsage(Long poolBytes, long handedOut, List<StationQuota> stations) {}
}
