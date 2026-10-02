/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The notifier as its senders see it: an audience in, rows out, and the same rule for who holds a
 * permission as the one every permission check answers by.
 */
class NotifierTest extends RepositoryTestBase {
    private static final NotificationData.NotificationLink LINK = new NotificationData.NotificationLink("news-list");

    private static Notifier notifier;
    private static Station station;
    private static List<StationMember> members;
    private static Cluster cluster;
    private static ClusterMember clusterAdmin;
    private static ClusterMember clusterUser;

    @BeforeAll
    static void setup() {
        notifier = newNotifier();
        station = stationRepo.create("Notifier Station");
        var plain = member("plain");
        var byType = member("by-type");
        stationMemberRepo.setUserType(byType.id(), StationUserType.MANAGER);
        var byStationGrant = member("by-station-grant");
        stationMemberRepo.setUserType(byStationGrant.id(), StationUserType.TEAM);
        stationMemberRepo.setUserTypePermissions(
                station.id(), StationUserType.TEAM, List.of(permissionId(StationPermission.EVENT_MANAGER)));
        var direct = member("direct");
        stationMemberRepo.grantPermission(direct.id(), permissionId(StationPermission.NEWS_MANAGER));
        var byGroup = member("by-group");
        var group = memberGroupRepo.create(station.id(), "Notifier Group");
        memberGroupRepo.addGroupPermission(group.id(), permissionId(StationPermission.STATION_ADMINISTRATOR));
        memberGroupRepo.addMember(group.id(), byGroup.id());
        var guardian = member("guardian");
        var ward = member("ward");
        stationMemberRepo.addManager(guardian.id(), ward.id());
        members = List.of(plain, byType, byStationGrant, direct, byGroup, guardian, ward);

        cluster = clusterService.create("Notifier Cluster", null);
        clusterAdmin = clusterService.addMember(
                cluster.id(),
                accountRepo
                        .create("notifier-admin@test.com", "Notifier", "Admin")
                        .id(),
                ClusterUserType.CLUSTER_ADMIN);
        clusterUser = clusterService.addMember(
                cluster.id(),
                accountRepo.create("notifier-user@test.com", "Notifier", "User").id(),
                ClusterUserType.CLUSTER_USER);
    }

    @AfterAll
    static void cleanup() {
        clusterService.delete(cluster.id());
        stationRepo.delete(station.id());
    }

    /**
     * For every member and every permission, the notifier reaches a member exactly when the
     * resolver that answers for one member says they hold it.
     */
    @Test
    void holdersAreWhoTheResolverSaysHoldsThePermission() {
        for (var permission : StationPermission.values()) {
            var data = data();
            notifier.notify(
                    StationAudience.holders(station.id(), permission),
                    NotificationType.NEW_NEWS,
                    data,
                    Delivery.EVERY_TIME);
            for (var member : members) {
                boolean holds = memberPermissionResolver.resolve(member.id()).contains(permission);
                boolean told = told(member, data);
                assertEquals(holds, told, "%s for member %s".formatted(permission, member.id()));
            }
        }
    }

    @Test
    void aStationAudienceIsWrittenAndAnEmptyOneCostsNothing() {
        var data = data();
        assertEquals(
                2,
                notifier.notify(
                        StationAudience.members(
                                List.of(members.get(0).id(), members.get(1).id())),
                        NotificationType.NEW_NEWS,
                        data,
                        Delivery.ONCE_WHILE_UNREAD));
        assertEquals(
                0,
                countStatements(() -> notifier.notify(
                        StationAudience.members(List.of()), NotificationType.NEW_NEWS, data(), Delivery.EVERY_TIME)));
    }

    @Test
    void clusterHoldersAreResolvedAndTheActorLeftOut() {
        var data = clusterData();
        int written = notifier.notify(
                ClusterAudience.holders(cluster.id(), ClusterPermission.CLUSTER_ADMINISTRATOR)
                        .and(ClusterAudience.members(List.of(clusterUser.id())))
                        .except(clusterUser.id()),
                NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                data,
                Delivery.EVERY_TIME);

        assertEquals(1, written);
        assertEquals(1, notificationRepo.countUnacknowledged(Recipient.clusterMember(clusterAdmin.id())));
        assertEquals(0, notificationRepo.countUnacknowledged(Recipient.clusterMember(clusterUser.id())));
        assertEquals(
                0,
                notifier.notify(
                        ClusterAudience.members(List.of()),
                        NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                        data,
                        Delivery.EVERY_TIME));
    }

    @Test
    void holdersOfTwoClustersCannotBeJoined() {
        assertThrows(IllegalArgumentException.class, () -> ClusterAudience.holders(1, ClusterPermission.USER)
                .and(ClusterAudience.holders(2, ClusterPermission.USER)));
        var audience = ClusterAudience.members(List.of(1));
        assertEquals(audience, audience.except(null));
    }

    @Test
    void aNotificationWithoutALinkIsRefused() {
        var withoutLink = NotificationData.of(new NotificationParams.NewNews("t", null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> notifier.notify(
                        StationAudience.member(members.get(0).id()),
                        NotificationType.NEW_NEWS,
                        withoutLink,
                        Delivery.EVERY_TIME));
        assertThrows(
                IllegalArgumentException.class,
                () -> notifier.notify(
                        StationAudience.member(members.get(0).id()),
                        NotificationType.NEW_NEWS,
                        null,
                        Delivery.EVERY_TIME));
    }

    @Test
    void withdrawingTakesTheUnreadOnesOrEverythingPointingAtAnEntity() {
        var reader = members.get(0);
        var link = new NotificationData.NotificationLink(
                "news-detail", Map.of("id", UUID.randomUUID().toString()));
        var data = NotificationData.of(new NotificationParams.NewNews("withdrawn", null, null), link);
        notifier.notify(StationAudience.member(reader.id()), NotificationType.NEW_NEWS, data, Delivery.EVERY_TIME);

        notifier.withdraw(NotificationType.NEW_NEWS, link);
        assertFalse(told(reader, data));

        notifier.notify(StationAudience.member(reader.id()), NotificationType.NEWS_COMMENT, data, Delivery.EVERY_TIME);
        notifier.withdrawAll(link);
        assertTrue(notificationRepo.findRecent(Recipient.stationMember(reader.id())).stream()
                .noneMatch(n -> n.data().equals(data)));
    }

    private static StationMember member(String name) {
        var account = accountRepo.create("notifier-" + name + "@test.com", "Notifier", name);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.grantPermission(member.id(), permissionId(StationPermission.USER));
        return member;
    }

    private static int permissionId(StationPermission permission) {
        return stationMemberRepo.findPermissionByName(permission).orElseThrow().id();
    }

    private static NotificationData data() {
        return NotificationData.of(
                new NotificationParams.NewNews(UUID.randomUUID().toString(), null, null), LINK);
    }

    private static NotificationData clusterData() {
        return NotificationData.of(
                new NotificationParams.ClusterApplicationSubmitted(
                        UUID.randomUUID().toString()),
                new NotificationData.NotificationLink("cluster-applications"));
    }

    private static boolean told(StationMember member, NotificationData data) {
        return notificationRepo.findUnacknowledged(Recipient.stationMember(member.id())).stream()
                .anyMatch(n -> n.data().equals(data));
    }
}
