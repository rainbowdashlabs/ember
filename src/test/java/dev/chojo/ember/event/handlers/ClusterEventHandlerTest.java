/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.event.events.ClusterApplicationResolved;
import dev.chojo.ember.event.events.ClusterApplicationSubmitted;
import dev.chojo.ember.event.events.ClusterApplicationWithdrawn;
import dev.chojo.ember.event.events.ClusterStationReleased;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Who hears about what a cluster and its stations do to each other.
 *
 * <p>The routing is the whole point of these handlers, so that is what is checked: the cluster's own people
 * hear about a request arriving, and the station's owner hears about the answer.
 */
class ClusterEventHandlerTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static NotificationService notificationService;

    @BeforeAll
    static void setup() {
        notificationService = mock(NotificationService.class);
    }

    private int clusterWithAdmin() {
        int n = NAMES.incrementAndGet();
        var cluster = clusterService.create("Kreisverband Ereignis " + n, null);
        var account = accountRepo.create("clusterevent" + n + "@test.com", "Clus", "Event" + n);
        clusterService.addMember(cluster.id(), account.id(), ClusterUserType.CLUSTER_ADMIN);
        return cluster.id();
    }

    @Test
    void aRequestArrivingReachesThePeopleWhoDecideAboutStations() {
        reset(notificationService);
        int clusterId = clusterWithAdmin();
        List<Integer> expected = clusterService.findMemberIdsWith(clusterId, ClusterPermission.CLUSTER_STATIONS);

        new ClusterApplicationSubmittedHandler(notificationService, clusterService)
                .handle(new ClusterApplicationSubmitted(clusterId, 1, "Wache Nord"));

        verify(notificationService)
                .notifyClusterMembersIfAbsent(
                        eq(expected),
                        eq(NotificationType.CLUSTER_APPLICATION_SUBMITTED),
                        any(NotificationData.class),
                        eq(null));
    }

    @Test
    void aRequestTakenBackReachesTheSamePeople() {
        reset(notificationService);
        int clusterId = clusterWithAdmin();
        List<Integer> expected = clusterService.findMemberIdsWith(clusterId, ClusterPermission.CLUSTER_STATIONS);

        new ClusterApplicationWithdrawnHandler(notificationService, clusterService)
                .handle(new ClusterApplicationWithdrawn(1, clusterId, "Wache Nord"));

        verify(notificationService)
                .notifyClusterMembersIfAbsent(
                        eq(expected),
                        eq(NotificationType.CLUSTER_APPLICATION_WITHDRAWN),
                        any(NotificationData.class),
                        eq(null));
    }

    @Test
    void theAnswerGoesToTheOwnerWhoAsked() {
        reset(notificationService);
        int n = NAMES.incrementAndGet();
        var station = stationRepo.create("Wache Antwort " + n);
        var account = accountRepo.create("clusteranswer" + n + "@test.com", "Ant", "Wort" + n);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationRepo.setOwner(station.id(), member.id());

        var handler = new ClusterApplicationResolvedHandler(notificationService, stationRepo);
        handler.handle(new ClusterApplicationResolved(station.id(), "Kreisverband Ja", true, null));
        verify(notificationService)
                .notifyIfAbsent(
                        eq(member.id()),
                        eq(NotificationType.CLUSTER_APPLICATION_APPROVED),
                        any(NotificationData.class));

        handler.handle(new ClusterApplicationResolved(station.id(), "Kreisverband Nein", false, "Zu weit weg"));
        verify(notificationService)
                .notifyIfAbsent(
                        eq(member.id()), eq(NotificationType.CLUSTER_APPLICATION_DENIED), any(NotificationData.class));
    }

    @Test
    void aReleasedStationTellsItsOwner() {
        reset(notificationService);
        int n = NAMES.incrementAndGet();
        var station = stationRepo.create("Wache Entlassen " + n);
        var account = accountRepo.create("clusterreleased" + n + "@test.com", "Ent", "Lassen" + n);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationRepo.setOwner(station.id(), member.id());

        new ClusterStationReleasedHandler(notificationService, stationRepo)
                .handle(new ClusterStationReleased(station.id(), "Kreisverband Weg"));

        verify(notificationService)
                .notifyIfAbsent(
                        eq(member.id()), eq(NotificationType.CLUSTER_STATION_RELEASED), any(NotificationData.class));
    }

    @Test
    void aStationWithoutAnOwnerHasNobodyToTell() {
        reset(notificationService);
        var station = stationRepo.create("Wache Ohne Leitung " + NAMES.incrementAndGet());

        new ClusterApplicationResolvedHandler(notificationService, stationRepo)
                .handle(new ClusterApplicationResolved(station.id(), "Kreisverband Egal", true, null));
        new ClusterStationReleasedHandler(notificationService, stationRepo)
                .handle(new ClusterStationReleased(station.id(), "Kreisverband Egal"));

        verify(notificationService, never()).notifyIfAbsent(anyInt(), any(), any());
    }
}
