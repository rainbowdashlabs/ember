/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterModuleDenied;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.entity.ClusterStorageConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

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
 *   <li>a storage backend is where its stations' files are kept, which is the cluster's to choose.
 * </ul>
 *
 * <p>Room is the fourth and lives in {@link ClusterStorageQuotaService}, because handing out portions of a
 * pool is arithmetic with a table of its own rather than a rule written into a station row.
 */
@Singleton
public class ClusterGovernanceService {
    private static final Logger log = LoggerFactory.getLogger(ClusterGovernanceService.class);

    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final ClusterStorageConfigRepository storageConfigRepository;
    private final StorageBackendResolver backendResolver;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterGovernanceService(
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            ClusterStorageConfigRepository storageConfigRepository,
            StorageBackendResolver backendResolver,
            DomainEventBus eventBus) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.storageConfigRepository = storageConfigRepository;
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

    public Optional<StationStorageBackendConfig> findStorageBackend(int clusterId) {
        return storageConfigRepository.findCurrent(clusterId).map(ClusterStorageConfig::config);
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
            storageConfigRepository.retireCurrent(clusterId);
        } else {
            storageConfigRepository.insertCurrent(clusterId, config);
        }
        backendResolver.invalidateStations(clusterRepository.findStationIds(clusterId));
        log.info("Cluster {} storage backend set to {}", clusterId, config != null ? config.type() : "the default");
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }
}
