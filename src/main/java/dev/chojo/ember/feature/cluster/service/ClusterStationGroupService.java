/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterStationGroup;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterStationGroupRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * How an association files its stations.
 *
 * <p>A filing rather than a partition: a station sits in as many groups as the association finds useful,
 * because grouping by region and grouping by equipment are two different questions about the same station.
 * What it buys is a question asked of some stations rather than all of them.
 */
@Singleton
public class ClusterStationGroupService {
    private static final Logger log = LoggerFactory.getLogger(ClusterStationGroupService.class);

    private final ClusterStationGroupRepository groupRepository;
    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;

    @Inject
    public ClusterStationGroupService(
            ClusterStationGroupRepository groupRepository,
            ClusterRepository clusterRepository,
            StationRepository stationRepository) {
        this.groupRepository = groupRepository;
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
    }

    public List<ClusterStationGroup> findByCluster(int clusterId) {
        return groupRepository.findByCluster(clusterId);
    }

    /**
     * Files a new group.
     *
     * @param clusterId the association
     * @param name      what it is called
     * @return the group
     */
    public ClusterStationGroup create(int clusterId, String name) {
        requireCluster(clusterId);
        String trimmed = requireName(name);
        requireNameFree(clusterId, trimmed, null);
        ClusterStationGroup group = groupRepository.create(clusterId, trimmed);
        log.info("Cluster {} filed a station group '{}'", clusterId, trimmed);
        return group;
    }

    /**
     * @param clusterId the association
     * @param groupId   the group
     * @param name      what it is called now
     */
    public void rename(int clusterId, int groupId, String name) {
        requireOwnGroup(clusterId, groupId);
        String trimmed = requireName(name);
        requireNameFree(clusterId, trimmed, groupId);
        groupRepository.rename(groupId, trimmed);
    }

    /**
     * Removes a filing, unless something is keyed to it.
     *
     * <p>Refused rather than cascaded: deleting a way of filing stations would otherwise silently delete
     * questions and every answer anybody gave to them, or quietly switch a denied module back on at every
     * station that was in the group.
     *
     * @param clusterId the association
     * @param groupId   the group
     */
    public void delete(int clusterId, int groupId) {
        requireOwnGroup(clusterId, groupId);
        int questions = groupRepository.countFieldsUsing(groupId);
        if (questions > 0) {
            throw new BadRequestResponse(
                    "%d question(s) are asked of this group. Point them somewhere else first.".formatted(questions));
        }
        int denials = clusterRepository.countDenialsUsingGroup(groupId);
        if (denials > 0) {
            throw new BadRequestResponse(
                    "%d module(s) are switched off for this group. Switch them back on first.".formatted(denials));
        }
        groupRepository.delete(groupId);
        log.info("Cluster {} removed station group {}", clusterId, groupId);
    }

    /**
     * The stations in one group.
     *
     * @param clusterId the association
     * @param groupId   the group
     * @return them, in the order the association's station list uses
     */
    public List<Station> findStations(int clusterId, int groupId) {
        requireOwnGroup(clusterId, groupId);
        List<Station> stations = new ArrayList<>();
        for (int stationId : groupRepository.findStationIds(groupId)) {
            stationRepository.findById(stationId).ifPresent(stations::add);
        }
        stations.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
        return stations;
    }

    /**
     * Replaces what is filed under one group.
     *
     * <p>Only the association's own stations, and never its own store: that station has no people, answers no
     * question and belongs on no list of stations somebody joined.
     *
     * @param clusterId   the association
     * @param groupId     the group
     * @param stationUids the stations that are in it afterwards
     */
    public void setStations(int clusterId, int groupId, List<UUID> stationUids) {
        Cluster cluster = requireCluster(clusterId);
        requireOwnGroup(clusterId, groupId);

        List<Integer> stationIds = new ArrayList<>();
        for (UUID uid : stationUids) {
            Station station =
                    stationRepository.findByUid(uid).orElseThrow(() -> new BadRequestResponse("No such station"));
            if (station.clusterId() == null || station.clusterId() != clusterId) {
                throw new BadRequestResponse("That station does not belong to this association");
            }
            if (station.id() == cluster.homeStationId()) {
                throw new BadRequestResponse("The association's own store is not one of its stations");
            }
            stationIds.add(station.id());
        }

        groupRepository.setStations(groupId, stationIds);
        log.info("Cluster {} filed {} station(s) under group {}", clusterId, stationIds.size(), groupId);
    }

    /**
     * Takes a station out of every filing, which is what leaving the association means.
     *
     * @param stationId the station being released
     */
    public void forgetStation(int stationId) {
        groupRepository.deleteMembershipsOfStation(stationId);
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }

    private void requireOwnGroup(int clusterId, int groupId) {
        boolean own = groupRepository
                .findById(groupId)
                .filter(group -> group.clusterId() == clusterId)
                .isPresent();
        if (!own) throw new NotFoundResponse("No such station group");
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A group needs a name");
        return name.trim();
    }

    private void requireNameFree(int clusterId, String name, Integer exceptGroupId) {
        boolean taken = groupRepository.findByCluster(clusterId).stream()
                .filter(group -> exceptGroupId == null || group.id() != exceptGroupId)
                .anyMatch(group -> group.name().equalsIgnoreCase(name));
        if (taken) throw new BadRequestResponse("This association already files a group under that name");
    }
}
