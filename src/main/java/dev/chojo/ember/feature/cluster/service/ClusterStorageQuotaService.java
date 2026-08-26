/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterQuotaChanged;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.ClusterQuotaDefaults;
import dev.chojo.ember.feature.storage.entity.ClusterStationQuota;
import dev.chojo.ember.feature.storage.entity.ClusterStorageQuotaPreset;
import dev.chojo.ember.feature.storage.entity.StationQuotas;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.feature.storage.repository.ClusterStorageQuotaRepository;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The room a cluster hands out.
 *
 * <p>The instance grants the cluster a pool and the cluster portions it out, which is arithmetic rather than
 * a rule: the sum of what every station has been promised may not go past the pool. The cluster's own store
 * is one of those stations, because the files a cluster keeps are kept there and are no more free than
 * anybody else's.
 *
 * <p>The cluster's numbers are its own. They are written where the cluster keeps them rather than into the
 * station row an instance administrator writes, so neither can silently replace the other and the pool adds
 * up what was actually promised.
 */
@Singleton
public class ClusterStorageQuotaService {
    private static final Logger log = LoggerFactory.getLogger(ClusterStorageQuotaService.class);

    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final ClusterStorageQuotaRepository quotaRepository;
    private final StorageQuotaService quotaService;
    private final StorageUsageRepository usageRepository;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterStorageQuotaService(
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            ClusterStorageQuotaRepository quotaRepository,
            StorageQuotaService quotaService,
            StorageUsageRepository usageRepository,
            DomainEventBus eventBus) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.quotaRepository = quotaRepository;
        this.quotaService = quotaService;
        this.usageRepository = usageRepository;
        this.eventBus = eventBus;
    }

    // -- The pool --

    /**
     * The pool the instance grants the cluster.
     *
     * @param clusterId the cluster
     * @param poolBytes how much it may hand out in total, or {@code null} for no cap
     */
    public void setStoragePool(int clusterId, Long poolBytes) {
        requireCluster(clusterId);
        clusterRepository.setStoragePool(clusterId, poolBytes);
        log.info("Cluster {} was granted a pool of {}", clusterId, poolBytes);
    }

    // -- Defaults --

    /**
     * What the cluster gives a station it has granted nothing of its own.
     *
     * @param clusterId the cluster
     * @return its defaults, every dimension {@code null} when it has set none
     */
    public ClusterQuotaDefaults findDefaults(int clusterId) {
        requireCluster(clusterId);
        return quotaRepository.findDefaults(clusterId);
    }

    /**
     * Sets what the cluster gives a station it has granted nothing of its own.
     *
     * <p>Not weighed against the pool. A default is not a promise to any one station, and a cluster that
     * writes one has not yet handed anything out; what the pool bounds is what was granted.
     *
     * @param defaults the seven dimensions, any of them {@code null} to leave that one to the instance
     */
    public void setDefaults(ClusterQuotaDefaults defaults) {
        requireCluster(defaults.clusterId());
        requirePositive(
                defaults.quotaBytes(),
                defaults.quotaKbBytes(),
                defaults.quotaBoardBytes(),
                defaults.quotaImagesBytes(),
                defaults.quotaPagesBytes(),
                defaults.perFileBytes(),
                defaults.perImageBytes());
        quotaRepository.setDefaults(defaults);
        log.info("Cluster {} set the room it gives its stations by default", defaults.clusterId());
    }

    // -- Tiers --

    public List<ClusterStorageQuotaPreset> findPresets(int clusterId) {
        requireCluster(clusterId);
        return quotaRepository.findPresets(clusterId);
    }

    /**
     * Adds a tier the cluster can hand to its stations.
     *
     * @throws BadRequestResponse when the name is blank or already taken in this cluster
     */
    public ClusterStorageQuotaPreset createPreset(
            int clusterId,
            String name,
            long total,
            long kb,
            long board,
            long images,
            long pages,
            long perFile,
            long perImage) {
        requireCluster(clusterId);
        String trimmed = requireName(name);
        requireFreeName(clusterId, trimmed, 0);
        requirePositive(total, kb, board, images, pages, perFile, perImage);
        var preset =
                quotaRepository.createPreset(clusterId, trimmed, total, kb, board, images, pages, perFile, perImage);
        log.info("Cluster {} added the storage tier '{}'", clusterId, preset.name());
        return preset;
    }

    /**
     * Changes a tier.
     *
     * <p>The stations already on it keep the numbers they were given. Applying a tier is an act rather than a
     * subscription, and a station's room changing because somebody edited a tier elsewhere is not something
     * anybody asked for.
     */
    public void updatePreset(
            int clusterId,
            int presetId,
            String name,
            long total,
            long kb,
            long board,
            long images,
            long pages,
            long perFile,
            long perImage) {
        requirePreset(clusterId, presetId);
        String trimmed = requireName(name);
        requireFreeName(clusterId, trimmed, presetId);
        requirePositive(total, kb, board, images, pages, perFile, perImage);
        quotaRepository.updatePreset(presetId, trimmed, total, kb, board, images, pages, perFile, perImage);
        log.info("Cluster {} reshaped the storage tier '{}' to {} bytes in total", clusterId, trimmed, total);
    }

    /**
     * Removes a tier, leaving the stations that were put on it exactly where they are.
     */
    public void deletePreset(int clusterId, int presetId) {
        requirePreset(clusterId, presetId);
        quotaRepository.deletePreset(presetId);
        log.info("Cluster {} removed a storage tier", clusterId);
    }

    /**
     * Puts several stations on one tier at once.
     *
     * <p>The pool is weighed once for the whole application rather than station by station, so a reshuffle
     * that balances in the end is not refused halfway through it.
     *
     * @param clusterId   the cluster
     * @param presetId    the tier
     * @param stationUids the stations to put on it
     * @throws BadRequestResponse when a station is not this cluster's, or the cluster would be promising more
     *                            than it has
     */
    public void applyPreset(int clusterId, int presetId, List<UUID> stationUids) {
        Cluster cluster = requireCluster(clusterId);
        var preset = requirePreset(clusterId, presetId);
        if (stationUids == null || stationUids.isEmpty()) throw new BadRequestResponse("No station named");

        List<Integer> stationIds = stationUids.stream()
                .map(uid -> requireStationOf(cluster, uid).id())
                .toList();

        if (cluster.storagePoolBytes() != null) {
            long others = quotaRepository.findGrants(clusterId).stream()
                    .filter(grant -> !stationIds.contains(grant.stationId()))
                    .map(ClusterStationQuota::quotaBytes)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .sum();
            long promised = others + preset.total() * stationIds.size();
            if (promised > cluster.storagePoolBytes()) {
                throw new BadRequestResponse(
                        "That is more than the cluster has left. Its pool is %d bytes and %d would be handed out."
                                .formatted(cluster.storagePoolBytes(), promised));
            }
        }

        quotaRepository.applyPreset(presetId, clusterId, stationIds);
        for (int stationId : stationIds) {
            eventBus.publish(new ClusterQuotaChanged(stationId, cluster.name(), preset.total()));
        }
        log.info("Cluster {} put {} station(s) on the tier '{}'", clusterId, stationIds.size(), preset.name());
    }

    // -- Grants --

    /**
     * Hands one station its share of the pool, in as many of the seven dimensions as the cluster cares about.
     *
     * <p>Setting the numbers by hand takes the station off whatever tier it was on, because the numbers no
     * longer come from there and a tier name that lies is worse than no tier name.
     *
     * @param clusterId  the cluster
     * @param stationUid the station receiving the room, which may be the cluster's own store
     * @param grant      the seven dimensions, any of them {@code null} to fall back to the cluster's defaults
     * @throws BadRequestResponse when that station is not this cluster's, or the pool will not stretch
     */
    public void setGrant(int clusterId, UUID stationUid, Dimensions grant) {
        Cluster cluster = requireCluster(clusterId);
        Station station = requireStationOf(cluster, stationUid);
        requirePositive(
                grant.totalBytes(),
                grant.kbBytes(),
                grant.boardBytes(),
                grant.imagesBytes(),
                grant.pagesBytes(),
                grant.perFileBytes(),
                grant.perImageBytes());
        requirePoolStretches(cluster, station.id(), grant.totalBytes());

        quotaRepository.setGrant(new ClusterStationQuota(
                station.id(),
                clusterId,
                grant.totalBytes(),
                grant.kbBytes(),
                grant.boardBytes(),
                grant.imagesBytes(),
                grant.pagesBytes(),
                grant.perFileBytes(),
                grant.perImageBytes(),
                null));
        eventBus.publish(new ClusterQuotaChanged(station.id(), cluster.name(), grant.totalBytes()));
        log.info("Cluster {} gave station {} room of {}", clusterId, station.id(), grant.totalBytes());
    }

    /**
     * Hands the room back, so the station lives on the cluster's defaults again.
     *
     * @param clusterId  the cluster
     * @param stationUid the station
     */
    public void handBack(int clusterId, UUID stationUid) {
        Cluster cluster = requireCluster(clusterId);
        Station station = requireStationOf(cluster, stationUid);
        quotaRepository.deleteGrant(station.id());
        eventBus.publish(new ClusterQuotaChanged(station.id(), cluster.name(), null));
        log.info("Cluster {} took the room back from station {}", clusterId, station.id());
    }

    /**
     * Hands one station a total and leaves its other dimensions where they were.
     *
     * <p>What the storage screen has always done, kept because the screen that only knows about a total is
     * still the one in front of people.
     *
     * @param quotaBytes the total, or {@code null} to hand the room back altogether
     */
    public void setTotal(int clusterId, int stationId, Long quotaBytes) {
        Cluster cluster = requireCluster(clusterId);
        Station station = requireStation(cluster, stationId);
        if (quotaBytes == null) {
            quotaRepository.deleteGrant(station.id());
        } else {
            requirePositive(quotaBytes);
            requirePoolStretches(cluster, station.id(), quotaBytes);
            var existing = quotaRepository.findGrant(station.id());
            quotaRepository.setGrant(new ClusterStationQuota(
                    station.id(),
                    cluster.id(),
                    quotaBytes,
                    existing.map(ClusterStationQuota::quotaKbBytes).orElse(null),
                    existing.map(ClusterStationQuota::quotaBoardBytes).orElse(null),
                    existing.map(ClusterStationQuota::quotaImagesBytes).orElse(null),
                    existing.map(ClusterStationQuota::quotaPagesBytes).orElse(null),
                    existing.map(ClusterStationQuota::perFileBytes).orElse(null),
                    existing.map(ClusterStationQuota::perImageBytes).orElse(null),
                    null));
        }
        eventBus.publish(new ClusterQuotaChanged(station.id(), cluster.name(), quotaBytes));
        log.info("Cluster {} gave station {} a quota of {}", cluster.id(), station.id(), quotaBytes);
    }

    // -- What it all adds up to --

    /**
     * The whole picture: the pool, what has been promised out of it, and every station with what it was
     * granted, what that resolves to and what it is actually using.
     *
     * <p>Granted and resolved side by side because they answer different questions. Granted says what this
     * cluster decided; resolved says what the station may keep, which may come from the cluster's defaults or
     * from the instance behind them.
     *
     * @param clusterId the cluster
     * @return one row per station, the cluster's own store included
     */
    public Overview findOverview(int clusterId) {
        Cluster cluster = requireCluster(clusterId);
        var presets = quotaRepository.findPresets(clusterId);
        List<StationRoom> stations = new ArrayList<>();
        long handedOut = 0;

        for (var row : quotaRepository.findStationsWithGrants(clusterId)) {
            var grant = quotaRepository.findGrant(row.stationId());
            StationQuotas resolved = quotaService.resolveQuotas(row.stationId());
            List<StorageUsage> usage = usageRepository.findByStation(row.stationId());
            long used = usageRepository.totalEnforcedBytes(row.stationId());
            String presetName = presets.stream()
                    .filter(preset -> row.presetId() != null && preset.id() == row.presetId())
                    .map(ClusterStorageQuotaPreset::name)
                    .findFirst()
                    .orElse(null);
            if (row.quotaBytes() != null) handedOut += row.quotaBytes();
            stations.add(new StationRoom(
                    row.uid(),
                    row.name(),
                    row.stationId() == cluster.homeStationId(),
                    grant.map(ClusterStorageQuotaService::dimensionsOf).orElse(Dimensions.none()),
                    resolved,
                    used,
                    usage,
                    row.presetId(),
                    presetName));
        }

        return new Overview(
                cluster.storagePoolBytes(), handedOut, quotaRepository.findDefaults(clusterId), presets, stations);
    }

    // -- Guards --

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }

    private ClusterStorageQuotaPreset requirePreset(int clusterId, int presetId) {
        var preset = quotaRepository.findPreset(presetId).orElseThrow(() -> new NotFoundResponse("No such tier"));
        if (preset.clusterId() != clusterId) throw new NotFoundResponse("No such tier");
        return preset;
    }

    /**
     * The station, checked to be one this cluster may hand room to.
     *
     * <p>Its own store counts. A cluster's files live on the station it owns, and room for them comes out of
     * the same pool as everybody else's.
     */
    private Station requireStationOf(Cluster cluster, UUID stationUid) {
        Station station =
                stationRepository.findByUid(stationUid).orElseThrow(() -> new NotFoundResponse("No such station"));
        return requireStation(cluster, station.id());
    }

    private Station requireStation(Cluster cluster, int stationId) {
        Station station =
                stationRepository.findById(stationId).orElseThrow(() -> new NotFoundResponse("No such station"));
        boolean ownStore = station.id() == cluster.homeStationId();
        if (!ownStore && (station.clusterId() == null || station.clusterId() != cluster.id())) {
            throw new BadRequestResponse("That station does not belong to this cluster");
        }
        return station;
    }

    /**
     * Refuses a promise the cluster cannot keep.
     *
     * @param stationId the station about to be granted, weighed out of the sum so its old promise is replaced
     *                  rather than added to
     */
    private void requirePoolStretches(Cluster cluster, int stationId, Long totalBytes) {
        if (cluster.storagePoolBytes() == null || totalBytes == null) return;
        long othersTotal = quotaRepository.sumGrantedTotals(cluster.id(), stationId);
        if (othersTotal + totalBytes > cluster.storagePoolBytes()) {
            throw new BadRequestResponse(
                    "That is more than the cluster has left. Its pool is %d bytes and %d are already handed out."
                            .formatted(cluster.storagePoolBytes(), othersTotal));
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A tier needs a name");
        return name.trim();
    }

    private void requireFreeName(int clusterId, String name, int exceptPresetId) {
        boolean taken = quotaRepository.findPresets(clusterId).stream()
                .anyMatch(
                        preset -> preset.id() != exceptPresetId && preset.name().equalsIgnoreCase(name));
        if (taken) throw new BadRequestResponse("This cluster already has a tier called '%s'".formatted(name));
    }

    /** Room is a size, and a size below zero is a typing mistake rather than a rule anybody meant. */
    private static void requirePositive(Long... values) {
        for (Long value : values) {
            if (value != null && value < 0) throw new BadRequestResponse("Room cannot be less than nothing");
        }
    }

    private static Dimensions dimensionsOf(ClusterStationQuota grant) {
        return new Dimensions(
                grant.quotaBytes(),
                grant.quotaKbBytes(),
                grant.quotaBoardBytes(),
                grant.quotaImagesBytes(),
                grant.quotaPagesBytes(),
                grant.perFileBytes(),
                grant.perImageBytes());
    }

    /**
     * The seven dimensions, as somebody hands them out.
     *
     * <p>A {@code null} means the cluster is not deciding that one, and whatever stands behind it applies.
     */
    public record Dimensions(
            Long totalBytes,
            Long kbBytes,
            Long boardBytes,
            Long imagesBytes,
            Long pagesBytes,
            Long perFileBytes,
            Long perImageBytes) {
        public static Dimensions none() {
            return new Dimensions(null, null, null, null, null, null, null);
        }
    }

    /**
     * One station in the cluster's picture of its room.
     *
     * @param ownStore whether this is the cluster's own store rather than one of its member stations
     * @param granted  what this cluster decided for it, dimension by dimension
     * @param resolved what it may actually keep, with where each number came from
     * @param usedBytes what it is keeping, in the categories that count against a quota
     * @param usage    the same, broken down
     * @param presetId the tier it was put on, or {@code null} when its numbers were set by hand
     */
    public record StationRoom(
            UUID stationUid,
            String stationName,
            boolean ownStore,
            Dimensions granted,
            StationQuotas resolved,
            long usedBytes,
            List<StorageUsage> usage,
            Integer presetId,
            String presetName) {}

    /**
     * @param poolBytes the whole the cluster may hand out, or {@code null} when the instance set no cap
     * @param handedOut the sum of the totals it has promised, its own store included
     */
    public record Overview(
            Long poolBytes,
            long handedOut,
            ClusterQuotaDefaults defaults,
            List<ClusterStorageQuotaPreset> presets,
            List<StationRoom> stations) {}
}
