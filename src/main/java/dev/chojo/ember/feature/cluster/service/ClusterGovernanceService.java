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
import dev.chojo.ember.feature.cluster.repository.ClusterStationGroupRepository;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
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
 *   <li>a look-and-feel setting is a starting point unless the cluster marks it locked.
 * </ul>
 *
 * <p>Room lives in {@link ClusterStorageQuotaService}, because handing out portions of a pool is arithmetic
 * with a table of its own rather than a rule written into a station row, and where the files are kept lives
 * in {@link ClusterStorageBackendService}, because a decision about that is not the same fact as where the
 * bytes actually are.
 */
@Singleton
public class ClusterGovernanceService {
    private static final Logger log = LoggerFactory.getLogger(ClusterGovernanceService.class);

    private final ClusterRepository clusterRepository;
    private final ClusterStationGroupRepository stationGroupRepository;
    private final StationRepository stationRepository;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterGovernanceService(
            ClusterRepository clusterRepository,
            ClusterStationGroupRepository stationGroupRepository,
            StationRepository stationRepository,
            DomainEventBus eventBus) {
        this.clusterRepository = clusterRepository;
        this.stationGroupRepository = stationGroupRepository;
        this.stationRepository = stationRepository;
        this.eventBus = eventBus;
    }

    // -- Public wiki --

    /**
     * Whether the cluster's wiki stands on the public web, and how.
     *
     * <p>The wiki is the one of the station the cluster owns, so this is that station's setting. The
     * cluster cannot reach the screen that carries it: running a cluster is not running a station, and
     * only the content rights are translated. Without this the setting had no owner at all, and the
     * per-entry visibility field, which is drawn only where the mode is on, could never appear.
     *
     * @param clusterId the cluster
     * @return the mode of the station the cluster owns
     */
    public PublicKbMode findPublicKbMode(int clusterId) {
        return homeStation(clusterId).publicKbMode();
    }

    /**
     * Puts the cluster's wiki on the public web, or takes it off.
     *
     * @param clusterId the cluster
     * @param mode      what the public web may see
     */
    public void setPublicKbMode(int clusterId, PublicKbMode mode) {
        var station = homeStation(clusterId);
        stationRepository.updatePublicKbMode(station.id(), mode);
        log.info("Cluster {} set its public wiki to {}", clusterId, mode);
    }

    /**
     * The address the cluster's public wiki answers at, which is the identifier of the station it owns.
     *
     * <p>A readable name would do as well and the station has none, so the uid stands in. It is a public
     * identifier already: the public wiki of any station answers at it.
     *
     * @param clusterId the cluster
     * @return the uid to build the public address from
     */
    public UUID publicKbStationUid(int clusterId) {
        return homeStation(clusterId).uid();
    }

    /**
     * The station a cluster keeps its own things on, which is where its wiki lives.
     *
     * @param clusterId the cluster
     * @return the station id its knowledge, news and calendar belong to
     */
    public int homeStationId(int clusterId) {
        return homeStation(clusterId).id();
    }

    private Station homeStation(int clusterId) {
        return stationRepository
                .findById(requireCluster(clusterId).homeStationId())
                .orElseThrow(() -> new NotFoundResponse("No such station"));
    }

    // -- Modules --

    /**
     * What the cluster denies of one group of its stations, or of all of them.
     *
     * @param clusterId      the cluster
     * @param stationGroupId the group, or {@code null} for the denials that reach every station
     */
    public Set<StationModule> findDeniedModules(int clusterId, Integer stationGroupId) {
        requireOwnGroup(clusterId, stationGroupId);
        return clusterRepository.findDeniedModules(clusterId, stationGroupId);
    }

    /**
     * Sets which modules the cluster switches off, for one group of its stations or for all of them.
     *
     * <p>Nothing is deleted. A denied module stops being reachable and everything already in it stays where
     * it is, ready to reappear if the denial is lifted or the station released. The stations that were
     * actually using one are told, because a page disappearing without explanation is the kind of thing
     * people report as a fault.
     *
     * <p>Denials add up and never cancel: a station loses a module when the cluster denies it outright or
     * denies it for any group that station is in. So the stations told about a new denial are the ones it
     * actually reaches, which for a group is the stations filed under it and for everybody is all of them.
     *
     * @param clusterId      the cluster
     * @param stationGroupId the group it is deciding for, or {@code null} for every station
     * @param modules        the modules it now denies there
     */
    public void setDeniedModules(int clusterId, Integer stationGroupId, Set<StationModule> modules) {
        Cluster cluster = requireCluster(clusterId);
        requireOwnGroup(clusterId, stationGroupId);
        Set<StationModule> before = clusterRepository.findDeniedModules(clusterId, stationGroupId);
        Set<StationModule> newlyDenied = EnumSet.noneOf(StationModule.class);
        newlyDenied.addAll(modules);
        newlyDenied.removeAll(before);

        clusterRepository.setDeniedModules(clusterId, stationGroupId, modules);
        log.info("Cluster {} now denies {} for group {}", clusterId, modules, stationGroupId);

        for (StationModule module : newlyDenied) {
            for (Station station : reached(clusterId, stationGroupId)) {
                // Only the stations that had it switched on lose anything they can see
                if (stationRepository.findDisabledModules(station.id()).contains(module)) continue;
                eventBus.publish(new ClusterModuleDenied(station.id(), cluster.name(), module));
            }
        }
    }

    /** The stations a denial reaches: the group's, or every one of the cluster's. */
    private List<Station> reached(int clusterId, Integer stationGroupId) {
        List<Station> all = stationRepository.findByCluster(clusterId);
        if (stationGroupId == null) return all;
        Set<Integer> inGroup = Set.copyOf(stationGroupRepository.findStationIds(stationGroupId));
        return all.stream().filter(station -> inGroup.contains(station.id())).toList();
    }

    /** A group of another association is not this one's to decide for. */
    private void requireOwnGroup(int clusterId, Integer stationGroupId) {
        if (stationGroupId == null) return;
        boolean own = stationGroupRepository
                .findById(stationGroupId)
                .filter(group -> group.clusterId() == clusterId)
                .isPresent();
        if (!own) throw new BadRequestResponse("That group of stations belongs to another association");
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

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }
}
