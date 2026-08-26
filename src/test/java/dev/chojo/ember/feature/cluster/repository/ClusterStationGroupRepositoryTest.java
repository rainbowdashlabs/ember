/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two tables behind an association's filing of its stations.
 */
class ClusterStationGroupRepositoryTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    /**
     * Setting the stations replaces rather than adds: the caller hands over the whole set it means.
     */
    @Test
    void groupsAreCreatedRenamedAndFilled() {
        var cluster = clusterService.create("Kreisverband Ablage " + NAMES.incrementAndGet(), null);
        var first = clusterService.createStation(cluster.id(), "Wache Eins " + NAMES.incrementAndGet());
        var second = clusterService.createStation(cluster.id(), "Wache Zwei " + NAMES.incrementAndGet());

        var group = clusterStationGroupRepo.create(cluster.id(), "Nordkreis");
        clusterStationGroupRepo.rename(group.id(), "Nordkreis und Küste");
        assertEquals(
                "Nordkreis und Küste",
                clusterStationGroupRepo.findById(group.id()).orElseThrow().name());

        clusterStationGroupRepo.setStations(group.id(), List.of(first.id(), second.id()));
        assertEquals(2, clusterStationGroupRepo.findStationIds(group.id()).size());

        clusterStationGroupRepo.setStations(group.id(), List.of(second.id()));
        assertEquals(List.of(second.id()), clusterStationGroupRepo.findStationIds(group.id()));
        assertEquals(List.of(group.id()), clusterStationGroupRepo.findGroupIdsOfStation(second.id()));
        assertTrue(clusterStationGroupRepo.findGroupIdsOfStation(first.id()).isEmpty());

        clusterStationGroupRepo.deleteMembershipsOfStation(second.id());
        assertTrue(clusterStationGroupRepo.findStationIds(group.id()).isEmpty());

        clusterService.releaseStation(cluster.id(), first.id());
        clusterService.releaseStation(cluster.id(), second.id());
        stationRepo.delete(first.id());
        stationRepo.delete(second.id());
        clusterService.delete(cluster.id());
    }

    /**
     * What a question reaches: the association's whole list when it names no group, and the group's stations
     * otherwise. This is what the collision rule compares.
     */
    @Test
    void whatAQuestionReachesIsEveryStationOrTheGroups() {
        var cluster = clusterService.create("Kreisverband Ablage " + NAMES.incrementAndGet(), null);
        var inside = clusterService.createStation(cluster.id(), "Wache Innen " + NAMES.incrementAndGet());
        var outside = clusterService.createStation(cluster.id(), "Wache Aussen " + NAMES.incrementAndGet());
        var group = clusterStationGroupRepo.create(cluster.id(), "Atemschutz " + NAMES.incrementAndGet());
        clusterStationGroupRepo.setStations(group.id(), List.of(inside.id()));

        var everybody = clusterStationGroupRepo.findStationIdsReachedBy(cluster.id(), null);
        assertTrue(everybody.contains(inside.id()) && everybody.contains(outside.id()));
        assertEquals(List.of(inside.id()), clusterStationGroupRepo.findStationIdsReachedBy(cluster.id(), group.id()));

        clusterService.releaseStation(cluster.id(), inside.id());
        clusterService.releaseStation(cluster.id(), outside.id());
        stationRepo.delete(inside.id());
        stationRepo.delete(outside.id());
        clusterService.delete(cluster.id());
    }

    /**
     * The foreign key refuses to drop a group questions are keyed to, so the service's friendlier refusal is
     * a message rather than the only thing standing between a filing and somebody's answers.
     */
    @Test
    void aGroupAQuestionIsAskedOfCannotBeDeletedAtTheDatabase() {
        var cluster = clusterService.create("Kreisverband Ablage " + NAMES.incrementAndGet(), null);
        var group = clusterStationGroupRepo.create(cluster.id(), "Atemschutz " + NAMES.incrementAndGet());
        var field = clusterProfileFieldRepo.create(
                cluster.id(),
                "Atemschutztauglich",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                group.id());

        assertEquals(1, clusterStationGroupRepo.countFieldsUsing(group.id()));
        assertThrows(Exception.class, () -> clusterStationGroupRepo.delete(group.id()));

        clusterProfileFieldRepo.delete(field.id());
        clusterStationGroupRepo.delete(group.id());
        assertTrue(clusterStationGroupRepo.findById(group.id()).isEmpty());

        clusterService.delete(cluster.id());
    }
}
