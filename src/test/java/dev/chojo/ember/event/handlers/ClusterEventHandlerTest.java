/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.events.ClusterApplicationResolved;
import dev.chojo.ember.event.events.ClusterApplicationSubmitted;
import dev.chojo.ember.event.events.ClusterApplicationWithdrawn;
import dev.chojo.ember.event.events.ClusterFieldValueChanged;
import dev.chojo.ember.event.events.ClusterMemberRoleChanged;
import dev.chojo.ember.event.events.ClusterModuleDenied;
import dev.chojo.ember.event.events.ClusterQuotaChanged;
import dev.chojo.ember.event.events.ClusterStationReleased;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Who hears about what a cluster and its stations do to each other.
 *
 * <p>The routing is the whole point of these handlers, so that is what is checked: the cluster's own people
 * hear about a request arriving, and the station's owner hears about the answer.
 */
class ClusterEventHandlerTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static Notifier notifier;

    @BeforeAll
    static void setup() {
        notifier = mock(Notifier.class);
    }

    @Test
    void aRequestArrivingReachesThePeopleWhoDecideAboutStations() {
        reset(notifier);

        new ClusterApplicationSubmittedHandler(notifier).handle(new ClusterApplicationSubmitted(4, 1, "Wache Nord"));

        verify(notifier)
                .notify(
                        eq(ClusterAudience.holders(4, ClusterPermission.CLUSTER_STATIONS)),
                        eq(NotificationType.CLUSTER_APPLICATION_SUBMITTED),
                        any(NotificationData.class),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void aRequestTakenBackReachesTheSamePeople() {
        reset(notifier);

        new ClusterApplicationWithdrawnHandler(notifier).handle(new ClusterApplicationWithdrawn(1, 4, "Wache Nord"));

        verify(notifier)
                .notify(
                        eq(ClusterAudience.holders(4, ClusterPermission.CLUSTER_STATIONS)),
                        eq(NotificationType.CLUSTER_APPLICATION_WITHDRAWN),
                        any(NotificationData.class),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void theAnswerGoesToTheOwnerWhoAsked() {
        reset(notifier);
        int n = NAMES.incrementAndGet();
        var station = stationRepo.create("Wache Antwort " + n);
        var account = accountRepo.create("clusteranswer" + n + "@test.com", "Ant", "Wort" + n);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationRepo.setOwner(station.id(), member.id());

        var handler = new ClusterApplicationResolvedHandler(notifier, stationRepo);
        handler.handle(new ClusterApplicationResolved(station.id(), "Kreisverband Ja", true, null));
        verify(notifier)
                .notify(
                        eq(StationAudience.member(member.id())),
                        eq(NotificationType.CLUSTER_APPLICATION_APPROVED),
                        any(NotificationData.class),
                        eq(Delivery.ONCE_WHILE_UNREAD));

        handler.handle(new ClusterApplicationResolved(station.id(), "Kreisverband Nein", false, "Zu weit weg"));
        verify(notifier)
                .notify(
                        eq(StationAudience.member(member.id())),
                        eq(NotificationType.CLUSTER_APPLICATION_DENIED),
                        any(NotificationData.class),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void aReleasedStationTellsItsOwner() {
        reset(notifier);
        int n = NAMES.incrementAndGet();
        var station = stationRepo.create("Wache Entlassen " + n);
        var account = accountRepo.create("clusterreleased" + n + "@test.com", "Ent", "Lassen" + n);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationRepo.setOwner(station.id(), member.id());

        new ClusterStationReleasedHandler(notifier, stationRepo)
                .handle(new ClusterStationReleased(station.id(), "Kreisverband Weg"));

        verify(notifier)
                .notify(
                        eq(StationAudience.member(member.id())),
                        eq(NotificationType.CLUSTER_STATION_RELEASED),
                        any(NotificationData.class),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void aStationWithoutAnOwnerHasNobodyToTell() {
        reset(notifier);
        var station = stationRepo.create("Wache Ohne Leitung " + NAMES.incrementAndGet());

        new ClusterApplicationResolvedHandler(notifier, stationRepo)
                .handle(new ClusterApplicationResolved(station.id(), "Kreisverband Egal", true, null));
        new ClusterStationReleasedHandler(notifier, stationRepo)
                .handle(new ClusterStationReleased(station.id(), "Kreisverband Egal"));

        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void aDeniedModuleReachesWhoeverManagesTheStationsModules() {
        reset(notifier);

        new ClusterGovernanceHandler(notifier)
                .handle(new ClusterModuleDenied(7, "Kreisverband Streng", StationModule.QUIZ));

        verify(notifier)
                .notify(
                        eq(StationAudience.holders(7, StationPermission.STATION_MODULES)),
                        eq(NotificationType.CLUSTER_MODULE_DENIED),
                        any(NotificationData.class),
                        eq(Delivery.EVERY_TIME));
    }

    /** A quota handed back to the instance carries no figure, and still has to say something. */
    @Test
    void aChangedQuotaReachesWhoeverRunsTheStation() {
        reset(notifier);
        var handler = new ClusterQuotaChangedHandler(notifier);

        handler.handle(new ClusterQuotaChanged(7, "Kreisverband Platz", 5_000_000L));
        handler.handle(new ClusterQuotaChanged(7, "Kreisverband Platz", null));

        verify(notifier, times(2))
                .notify(
                        eq(StationAudience.holders(7, StationPermission.STATION_MANAGER)),
                        eq(NotificationType.CLUSTER_QUOTA_CHANGED),
                        any(NotificationData.class),
                        eq(Delivery.EVERY_TIME));
    }

    @Test
    void aChangedStandingReachesTheOnePersonItConcerns() {
        reset(notifier);

        new ClusterMemberRoleChangedHandler(notifier).handle(new ClusterMemberRoleChanged(11, "Kreisverband Rolle"));

        verify(notifier)
                .notify(
                        eq(ClusterAudience.members(List.of(11))),
                        eq(NotificationType.CLUSTER_MEMBER_ROLE_CHANGED),
                        any(NotificationData.class),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void aProfileFilledInByTheClusterReachesTheMemberItIsAbout() {
        reset(notifier);

        new ClusterFieldValueChangedHandler(notifier)
                .handle(new ClusterFieldValueChanged(3, 21, "Kreisverband Profil", "Atemschutz"));

        verify(notifier)
                .notify(
                        eq(StationAudience.member(21)),
                        eq(NotificationType.CLUSTER_FIELD_VALUE_CHANGED),
                        any(NotificationData.class),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }
}
