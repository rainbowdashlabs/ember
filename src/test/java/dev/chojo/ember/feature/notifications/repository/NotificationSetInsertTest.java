/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.DigestItem;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.sql.Transactions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing one notification to many people in one statement: who the parts of an audience reach,
 * who is left out, and what "once while unread" means when two requests write at the same moment.
 */
class NotificationSetInsertTest extends RepositoryTestBase {
    private static final NotificationData.NotificationLink LINK = new NotificationData.NotificationLink("news-list");

    private static Station station;
    private static StationMember plain;
    private static StationMember manager;
    private static StationMember guardian;
    private static StationMember ward;
    private static StationMember formerMember;
    private static StationMember formerGuardian;
    private static StationMember guardianOfFormer;
    private static StationMember formerWard;
    private static StationMember optedOut;
    private static Cluster cluster;
    private static ClusterMember clusterMemberA;
    private static ClusterMember clusterMemberB;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Set Insert Station");
        plain = member("plain");
        manager = member("manager");
        guardian = member("guardian");
        ward = member("ward");
        formerMember = member("former");
        formerGuardian = member("former-guardian");
        guardianOfFormer = member("guardian-of-former");
        formerWard = member("former-ward");
        optedOut = member("opted-out");

        stationMemberRepo
                .findPermissionByName(StationPermission.NEWS_MANAGER)
                .ifPresent(permission -> stationMemberRepo.grantPermission(manager.id(), permission.id()));
        stationMemberRepo.addManager(guardian.id(), ward.id());
        stationMemberRepo.addManager(formerGuardian.id(), ward.id());
        stationMemberRepo.addManager(guardianOfFormer.id(), formerWard.id());
        stationMemberRepo.setFormer(formerMember.id(), true);
        stationMemberRepo.setFormer(formerGuardian.id(), true);
        stationMemberRepo.setFormer(formerWard.id(), true);
        notificationSettingsRepo.upsert(optedOut.id(), NotificationType.NEW_NEWS, false, false, true);

        cluster = clusterService.create("Set Insert Cluster", null);
        clusterMemberA = clusterService.addMember(
                cluster.id(), accountRepo.create("set-a@test.com", "Set", "A").id(), ClusterUserType.CLUSTER_USER);
        clusterMemberB = clusterService.addMember(
                cluster.id(), accountRepo.create("set-b@test.com", "Set", "B").id(), ClusterUserType.CLUSTER_USER);
    }

    @AfterAll
    static void cleanup() {
        clusterService.delete(cluster.id());
        stationRepo.delete(station.id());
    }

    @Test
    void membersAreToldOnceAndFormerMembersNotAtAll() {
        var data = news();
        int written = insert(
                StationAudience.members(List.of(plain.id(), plain.id(), formerMember.id()))
                        .and(StationAudience.member(plain.id())),
                data,
                Delivery.EVERY_TIME);

        assertEquals(1, written);
        assertEquals(1, rows(plain, data));
        assertEquals(0, rows(formerMember, data));
    }

    @Test
    void theWholeStationReachesEveryoneStillThereExceptTheActorAndWhoeverSwitchedTheTypeOff() {
        var data = news();
        insert(StationAudience.wholeStation(station.id()).except(manager.id()), data, Delivery.EVERY_TIME);

        assertEquals(1, rows(plain, data));
        assertEquals(1, rows(ward, data));
        assertEquals(0, rows(manager, data), "the actor is left out");
        assertEquals(0, rows(formerMember, data), "people who left are not told");
        assertEquals(0, rows(optedOut, data), "a type switched off in the app is not written");
    }

    @Test
    void holdersAreFoundByThePermissionRule() {
        var data = news();
        insert(StationAudience.holders(station.id(), StationPermission.NEWS_MANAGER), data, Delivery.EVERY_TIME);

        assertEquals(1, rows(manager, data));
        assertEquals(0, rows(plain, data));
    }

    @Test
    void guardiansOfAWardAreToldButNotTheWardNorAGuardianWhoLeft() {
        var data = news();
        insert(StationAudience.guardiansOf(List.of(ward.id(), formerWard.id())), data, Delivery.EVERY_TIME);

        assertEquals(1, rows(guardian, data));
        assertEquals(0, rows(ward, data));
        assertEquals(0, rows(formerGuardian, data), "a guardian who left is nobody's guardian");
        assertEquals(0, rows(guardianOfFormer, data), "a ward who left brings in no guardians");
    }

    @Test
    void aHouseholdIsTheMemberAndTheirGuardians() {
        var data = news();
        insert(StationAudience.household(List.of(ward.id())), data, Delivery.EVERY_TIME);

        assertEquals(1, rows(ward, data));
        assertEquals(1, rows(guardian, data));
    }

    @Test
    void aRestrictedEntityReachesOnlyWhoMayOpenIt() {
        var data = news();
        insert(StationAudience.visibleTo(station.id(), Optional.of(Set.of(plain.id()))), data, Delivery.EVERY_TIME);
        assertEquals(1, rows(plain, data));
        assertEquals(0, rows(ward, data));

        var open = news();
        insert(StationAudience.visibleTo(station.id(), Optional.empty()), open, Delivery.EVERY_TIME);
        assertEquals(1, rows(ward, open));
    }

    @Test
    void anAudienceReachingNobodyWritesNothing() {
        assertEquals(0, insert(StationAudience.members(List.of()), news(), Delivery.EVERY_TIME));
        assertTrue(StationAudience.members(List.of()).reachesNobody());
        assertFalse(StationAudience.guardiansOf(List.of(ward.id())).reachesNobody());
    }

    @Test
    void partsOfTwoStationsCannotBeJoined() {
        assertThrows(IllegalArgumentException.class, () -> StationAudience.wholeStation(1)
                .and(StationAudience.wholeStation(2)));
    }

    @Test
    void onceWhileUnreadWritesAgainOnlyAfterItWasRead() {
        var data = news();
        assertEquals(1, insert(StationAudience.member(plain.id()), data, Delivery.ONCE_WHILE_UNREAD));
        assertEquals(0, insert(StationAudience.member(plain.id()), data, Delivery.ONCE_WHILE_UNREAD));
        assertEquals(1, rows(plain, data));

        var written = notificationRepo.findUnacknowledged(plain.id()).stream()
                .filter(n -> n.data().equals(data))
                .findFirst()
                .orElseThrow();
        notificationRepo.acknowledge(written.id(), plain.id());

        assertEquals(1, insert(StationAudience.member(plain.id()), data, Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void onceWhileUnreadAlsoRespectsOneSentEveryTime() {
        var data = news();
        insert(StationAudience.member(plain.id()), data, Delivery.EVERY_TIME);

        assertEquals(0, insert(StationAudience.member(plain.id()), data, Delivery.ONCE_WHILE_UNREAD));
        assertEquals(1, rows(plain, data));
    }

    @Test
    void everyTimeAlwaysWrites() {
        var data = news();
        insert(StationAudience.member(plain.id()), data, Delivery.EVERY_TIME);
        insert(StationAudience.member(plain.id()), data, Delivery.EVERY_TIME);

        assertEquals(2, rows(plain, data));
    }

    @Test
    void twoRequestsSendingTheSameNoticeAtOnceWriteItOnce() throws Exception {
        var data = news();
        var audience = StationAudience.members(List.of(plain.id(), ward.id()));
        var barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> Transactions.call(() -> {
                    await(barrier);
                    return notificationRepo.insertForStation(
                            audience, NotificationType.NEW_NEWS, data, Delivery.ONCE_WHILE_UNREAD);
                })));
            }
            int written = 0;
            for (var result : results) written += result.get();

            assertEquals(2, written, "one row per person between the two requests");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, rows(plain, data));
        assertEquals(1, rows(ward, data));
    }

    @Test
    void aHundredMembersAreToldInOneStatement() {
        var crowd = stationRepo.create("Crowded Station");
        for (int i = 0; i < 100; i++) {
            stationMemberRepo.create(
                    crowd.id(),
                    accountRepo
                            .create("crowd-" + i + "@test.com", "Crowd", "No" + i)
                            .id());
        }
        var data = news();
        int[] written = new int[1];

        int statements = countStatements(() -> written[0] = notificationRepo.insertForStation(
                StationAudience.wholeStation(crowd.id()), NotificationType.NEW_NEWS, data, Delivery.EVERY_TIME));

        assertEquals(100, written[0]);
        assertEquals(1, statements);
        stationRepo.delete(crowd.id());
    }

    @Test
    void markingManyAsEmailedIsOneStatement() {
        var data = news();
        insert(StationAudience.members(List.of(plain.id(), ward.id(), guardian.id())), data, Delivery.EVERY_TIME);
        var ids = notificationRepo.findWaitingForDigest().stream()
                .map(DigestItem::notification)
                .filter(n -> n.data().equals(data))
                .map(Notification::id)
                .toList();

        int statements = countStatements(() -> notificationRepo.markEmailed(ids));

        assertEquals(3, ids.size());
        assertEquals(1, statements);
        assertTrue(notificationRepo.findWaitingForDigest().stream()
                .noneMatch(item -> ids.contains(item.notification().id())));
    }

    @Test
    void clusterMembersAreToldOnceWhileUnreadAndTheActorIsLeftOut() {
        var data =
                NotificationData.of(new NotificationParams.ClusterApplicationSubmitted(UUID.randomUUID() + ""), LINK);
        var both = List.of(clusterMemberA.id(), clusterMemberB.id());

        assertEquals(
                1,
                notificationRepo.insertForCluster(
                        both,
                        List.of(clusterMemberB.id()),
                        NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                        data,
                        Delivery.ONCE_WHILE_UNREAD));
        assertEquals(
                1,
                notificationRepo.insertForCluster(
                        both,
                        List.of(),
                        NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                        data,
                        Delivery.ONCE_WHILE_UNREAD),
                "only the one who has nothing waiting yet");
        assertEquals(
                2,
                notificationRepo.insertForCluster(
                        both, List.of(), NotificationType.CLUSTER_APPLICATION_SUBMITTED, data, Delivery.EVERY_TIME));
        assertEquals(2, notificationRepo.countUnacknowledgedForClusterMember(clusterMemberA.id()));
    }

    private static StationMember member(String name) {
        var account = accountRepo.create("set-insert-" + name + "@test.com", "Set", name);
        return stationMemberRepo.create(station.id(), account.id());
    }

    private static NotificationData news() {
        return NotificationData.of(
                new NotificationParams.NewNews(UUID.randomUUID().toString(), null, null), LINK);
    }

    private static int insert(StationAudience audience, NotificationData data, Delivery delivery) {
        return notificationRepo.insertForStation(audience, NotificationType.NEW_NEWS, data, delivery);
    }

    private static long rows(StationMember member, NotificationData data) {
        return notificationRepo.findUnacknowledged(member.id()).stream()
                .filter(n -> n.data().equals(data))
                .count();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
