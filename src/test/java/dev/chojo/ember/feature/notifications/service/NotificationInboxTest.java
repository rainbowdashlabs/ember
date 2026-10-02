/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Audience;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.ExpiryReminderKind;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * What a station member or a cluster follower is told through the notifier, and how they read and
 * clear it in their inbox.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationInboxTest extends RepositoryTestBase {
    private static Notifier notifier;
    private static NotificationInbox inbox;
    private static Station station;
    private static Account account1;
    private static Account account2;
    private static StationMember member1;
    private static StationMember member2;

    @BeforeAll
    static void setup() {
        inbox = new NotificationInbox(notificationRepo);
        notifier = newNotifier();

        station = stationRepo.create("NotifStation");
        account1 = accountRepo.create("notif1@test.com", "Notif", "One");
        account2 = accountRepo.create("notif2@test.com", "Notif", "Two");
        member1 = stationMemberRepo.create(station.id(), account1.id());
        member2 = stationMemberRepo.create(station.id(), account2.id());
        stationMemberRepo.findPermissionByName(StationPermission.USER).ifPresent(r -> {
            stationMemberRepo.grantPermission(member1.id(), r.id());
            stationMemberRepo.grantPermission(member2.id(), r.id());
        });
        stationMemberRepo.findPermissionByName(StationPermission.LOGIN).ifPresent(r -> {
            stationMemberRepo.grantPermission(member1.id(), r.id());
            stationMemberRepo.grantPermission(member2.id(), r.id());
        });
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account1.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(1)
    void notifySingleMember() {
        var data = NotificationData.of(
                new NotificationParams.NewNews("Title", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(StationAudience.member(member1.id()), NotificationType.NEW_NEWS, data);

        var unack = unread(member1.id());
        assertTrue(unack.stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
    }

    @Test
    @Order(2)
    void countUnacknowledged() {
        assertTrue(countUnread(member1.id()) >= 1);
        assertEquals(0, countUnread(member2.id()));
    }

    @Test
    @Order(3)
    void notifyStation() {
        var data = NotificationData.of(
                new NotificationParams.NewEvent("Test Event", "Description"),
                new NotificationData.NotificationLink("event-detail"));
        tell(StationAudience.wholeStation(station.id()), NotificationType.NEW_EVENT, data);

        assertTrue(unread(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_EVENT));
        assertTrue(unread(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_EVENT));
    }

    @Test
    @Order(4)
    void acknowledgeReducesCount() {
        int before = countUnread(member1.id());
        var first = unread(member1.id()).getFirst();
        acknowledge(first.id(), member1.id());
        assertEquals(before - 1, countUnread(member1.id()));
    }

    @Test
    @Order(5)
    void acknowledgeAllClearsAll() {
        int count = acknowledgeAll(member2.id());
        assertTrue(count >= 1);
        assertEquals(0, countUnread(member2.id()));
    }

    @Test
    @Order(10)
    void onceWhileUnreadDoesNotDuplicate() {
        var data = NotificationData.of(
                new NotificationParams.ProcurementRequested("Helm"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(StationAudience.member(member1.id()), NotificationType.PROCUREMENT_REQUESTED, data);
        int before = unread(member1.id()).size();

        tellOnce(StationAudience.member(member1.id()), NotificationType.PROCUREMENT_REQUESTED, data);
        int after = unread(member1.id()).size();
        assertEquals(before, after);
    }

    @Test
    @Order(20)
    void withdrawAllTakesEverythingPointingAtAnEntity() {
        var link = new NotificationData.NotificationLink("lost-and-found", Map.of("id", 4711));
        tell(
                StationAudience.member(member1.id()),
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(new NotificationParams.LostAndFoundNew("Blue hat"), link));
        assertTrue(unread(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.LOST_AND_FOUND_NEW));

        notifier.withdrawAll(link);

        assertFalse(unread(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.LOST_AND_FOUND_NEW));
    }

    @Test
    @Order(25)
    void findAll() {
        var all = recent(member1.id());
        assertNotNull(all);
        assertFalse(all.isEmpty());
        assertTrue(inbox.latestStamp(member1.id()).maxId() >= all.getFirst().id());
    }

    @Test
    @Order(26)
    void getNotificationSettings() {
        var settings = new NotificationPreferences(notificationSettingsRepo, clusterRepo, mock(EmailService.class))
                .settingsOf(member1.id());
        assertNotNull(settings);
    }

    @Test
    @Order(30)
    void aStationAudienceLeavesOutWhoeverActed() {
        acknowledgeAll(member1.id());
        acknowledgeAll(member2.id());

        var data = NotificationData.of(
                new NotificationParams.NewNews("Excluded Test", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(StationAudience.wholeStation(station.id()).except(member1.id()), NotificationType.NEW_NEWS, data);

        assertFalse(unread(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        assertTrue(unread(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
    }

    @Test
    @Order(31)
    void holdersOfAPermissionAreTold() {
        acknowledgeAll(member1.id());
        acknowledgeAll(member2.id());

        var data = NotificationData.of(
                new NotificationParams.NewNews("Role Notify", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(StationAudience.holders(station.id(), StationPermission.USER), NotificationType.NEW_NEWS, data);

        assertTrue(unread(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        assertTrue(unread(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
    }

    @Test
    @Order(32)
    void holdersOfAPermissionLeaveOutWhoeverActed() {
        acknowledgeAll(member1.id());
        acknowledgeAll(member2.id());

        var data = NotificationData.of(
                new NotificationParams.NewNews("Role Exclude", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(
                StationAudience.holders(station.id(), StationPermission.USER).except(member2.id()),
                NotificationType.NEW_NEWS,
                data);

        assertTrue(unread(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        assertFalse(unread(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
    }

    /**
     * A station manager is told about the work of every manager role, although none of those
     * rights was ever granted to them by name.
     */
    @Test
    @Order(32)
    void holdersReachAManagerByUserType() {
        var managerAccount = accountRepo.create("notif-type-manager@test.com", "Notif", "Manager");
        try {
            var manager = stationMemberRepo.create(station.id(), managerAccount.id());
            stationMemberRepo.setUserType(manager.id(), StationUserType.MANAGER);
            acknowledgeAll(member2.id());

            var data = NotificationData.of(
                    new NotificationParams.NewNews("Managers only", "Author", "Preview"),
                    new NotificationData.NotificationLink("dashboard-overview"));
            tell(
                    StationAudience.holders(station.id(), StationPermission.EVENT_MANAGER),
                    NotificationType.NEW_NEWS,
                    data);

            assertTrue(unread(manager.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
            assertFalse(unread(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        } finally {
            accountRepo.delete(managerAccount.id());
        }
    }

    @Test
    @Order(33)
    void membersNamedOneByOneAreTold() {
        acknowledgeAll(member1.id());
        acknowledgeAll(member2.id());

        var data = NotificationData.of(
                new NotificationParams.NewNews("Batch Notify", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(StationAudience.members(List.of(member1.id(), member2.id())), NotificationType.NEW_NEWS, data);

        assertTrue(countUnread(member1.id()) >= 1);
        assertTrue(countUnread(member2.id()) >= 1);
    }

    @Test
    @Order(34)
    void membersToldOnceLeaveOutWhoeverActedAndAreNotToldTwice() {
        acknowledgeAll(member1.id());
        acknowledgeAll(member2.id());

        var data = NotificationData.of(
                new NotificationParams.NewNews("IfAbsent", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tellOnce(
                StationAudience.members(List.of(member1.id(), member2.id())).except(member2.id()),
                NotificationType.NEW_NEWS,
                data);

        assertTrue(unread(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        assertEquals(0, countUnread(member2.id()));

        int before = unread(member1.id()).size();
        tellOnce(StationAudience.members(List.of(member1.id())), NotificationType.NEW_NEWS, data);
        int after = unread(member1.id()).size();
        assertEquals(before, after);
    }

    /**
     * What a cluster follower is told, read and acknowledged the way a station member's is.
     *
     * <p>The cluster side of notifications has always been written but never read back by anything
     * that a test covered, which is the same blind spot that let its digest go unnoticed for as long
     * as it did.
     */
    @Test
    @Order(46)
    void aClusterFollowerReadsAndClearsWhatTheyAreTold() {
        var cluster = clusterRepo.create("ReadingCluster", "keeps up with its partners", station.id());
        var follower = clusterRepo.addMember(cluster.id(), account2.id(), ClusterUserType.CLUSTER_USER);
        try {
            var data = NotificationData.of(
                    new NotificationParams.NewEvent("Partner drill", "next door"),
                    new NotificationData.NotificationLink("dashboard-overview"));
            var audience = ClusterAudience.members(List.of(follower.id()));

            tellOnce(audience, NotificationType.NEW_EVENT, data);
            tellOnce(audience, NotificationType.NEW_EVENT, data);

            assertEquals(1, clusterCountUnread(follower.id()), "and not told twice");
            assertFalse(clusterRecent(follower.id()).isEmpty());

            var first = clusterUnread(follower.id()).getFirst();
            clusterAcknowledge(first.id(), follower.id());
            assertEquals(0, clusterCountUnread(follower.id()));

            tellOnce(audience, NotificationType.NEW_EVENT, data);
            assertTrue(clusterAcknowledgeAll(follower.id()) >= 1);
        } finally {
            clusterRepo.delete(cluster.id());
        }
    }

    /** A reminder that repeats reaches a cluster member again while the earlier copy is still unread. */
    @Test
    @Order(46)
    void aClusterMemberIsToldAgainWhereRepeatingIsMeant() {
        var cluster = clusterRepo.create("RemindedCluster", "keeps being reminded", station.id());
        var office = clusterRepo.addMember(cluster.id(), account2.id(), ClusterUserType.CLUSTER_USER);
        try {
            var data = NotificationData.of(
                    new NotificationParams.ExpiryReminder(
                            ExpiryReminderKind.MEMBERS_DUE, "Maschinist gültig bis", null, null, null, "Anna", 1),
                    NotificationLinks.clusterMembers());
            var audience = ClusterAudience.members(List.of(office.id()));

            tell(audience, NotificationType.EXPIRY_REMINDER, data);
            tell(audience, NotificationType.EXPIRY_REMINDER, data);

            assertEquals(2, clusterCountUnread(office.id()));
        } finally {
            clusterRepo.delete(cluster.id());
        }
    }

    /**
     * One person with a station membership and a cluster membership reads two feeds, and marking
     * something read in one never reaches a notification of the other, whatever id is named.
     */
    @Test
    @Order(47)
    void theStationFeedAndTheClusterFeedNeverReachEachOther() {
        var cluster = clusterRepo.create("SeparateCluster", "keeps its feed apart", station.id());
        var office = clusterRepo.addMember(cluster.id(), account1.id(), ClusterUserType.CLUSTER_USER);
        try {
            acknowledgeAll(member1.id());
            var data = NotificationData.of(
                    new NotificationParams.NewEvent("Apart", "kept"),
                    new NotificationData.NotificationLink("dashboard-overview"));
            tell(StationAudience.member(member1.id()), NotificationType.NEW_EVENT, data);
            tell(ClusterAudience.members(List.of(office.id())), NotificationType.NEW_EVENT, data);
            var stationOne = unread(member1.id()).getFirst();
            var clusterOne = clusterUnread(office.id()).getFirst();

            clusterAcknowledge(stationOne.id(), office.id());
            acknowledge(clusterOne.id(), member1.id());
            assertEquals(1, countUnread(member1.id()));
            assertEquals(1, clusterCountUnread(office.id()));
            assertTrue(recent(member1.id()).stream().noneMatch(n -> n.id() == clusterOne.id()));
            assertTrue(clusterRecent(office.id()).stream().noneMatch(n -> n.id() == stationOne.id()));

            assertEquals(1, clusterAcknowledgeAll(office.id()));
            assertEquals(1, countUnread(member1.id()));
            assertEquals(1, acknowledgeAll(member1.id()));
        } finally {
            clusterRepo.delete(cluster.id());
        }
    }

    @Test
    @Order(50)
    void aMemberWhoSwitchedTheAppOffIsNotTold() {
        notificationSettingsRepo.upsert(member1.id(), NotificationType.WAITLIST_NEW_ENTRY, false, false, false);
        var data = NotificationData.of(
                new NotificationParams.WaitlistNewEntry("Child", "List A"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(StationAudience.member(member1.id()), NotificationType.WAITLIST_NEW_ENTRY, data);
        assertTrue(unread(member1.id()).stream().noneMatch(n -> n.type() == NotificationType.WAITLIST_NEW_ENTRY));
    }

    @Test
    @Order(51)
    void aMemberWhoSwitchedTheAppOffIsNotToldOnceEither() {
        notificationSettingsRepo.upsert(member2.id(), NotificationType.LENDING_NEW_REQUEST, false, false, false);
        int before = countUnread(member2.id());
        var data = NotificationData.of(
                new NotificationParams.LendingNewRequest("Station X", "Helmet"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tellOnce(StationAudience.member(member2.id()), NotificationType.LENDING_NEW_REQUEST, data);
        assertEquals(before, countUnread(member2.id()));
    }

    @Test
    @Order(52)
    void membersNamedOneByOneSkipWhoeverSwitchedTheAppOff() {
        notificationSettingsRepo.upsert(member1.id(), NotificationType.LENDING_STATUS_CHANGE, false, false, false);
        acknowledgeAll(member1.id());
        var data = NotificationData.of(
                new NotificationParams.LendingStatusChange("Station Y", LendingStatus.APPROVED),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(StationAudience.members(List.of(member1.id())), NotificationType.LENDING_STATUS_CHANGE, data);
        assertFalse(unread(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.LENDING_STATUS_CHANGE));
    }

    @Test
    @Order(53)
    void aWholeStationSkipsWhoeverSwitchedTheAppOff() {
        notificationSettingsRepo.upsert(member2.id(), NotificationType.LENDING_NEW_MESSAGE, false, false, false);
        acknowledgeAll(member2.id());
        var data = NotificationData.of(
                new NotificationParams.LendingNewMessage("Station Z", "Alice"),
                new NotificationData.NotificationLink("dashboard-overview"));
        tell(StationAudience.wholeStation(station.id()), NotificationType.LENDING_NEW_MESSAGE, data);
        assertFalse(unread(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.LENDING_NEW_MESSAGE));
    }

    @Test
    @Order(60)
    void everyKindOfNewsIsWritten() {
        acknowledgeAll(member1.id());
        var member = StationAudience.member(member1.id());
        tell(
                member,
                NotificationType.MOVEMENT_ADVANCED,
                NotificationData.of(
                        new NotificationParams.MovementMoved("Altes Teil zurückgenommen", "Helmet", StepActor.STATION),
                        new NotificationData.NotificationLink("inventory-exchanges")));
        tell(
                member,
                NotificationType.MOVEMENT_RAISED,
                NotificationData.of(
                        new NotificationParams.MovementRaised("Alice", "Helmet", "I need it"),
                        new NotificationData.NotificationLink("inventory-exchanges")));
        tell(
                member,
                NotificationType.PROFILE_FIELD_CHANGED,
                NotificationData.of(
                        new NotificationParams.ProfileFieldChanged("Alice", "Phone"),
                        new NotificationData.NotificationLink("members-detail", Map.of("id", 42))));
        tell(
                member,
                NotificationType.LOST_AND_FOUND_CLAIMED,
                NotificationData.of(
                        new NotificationParams.LostAndFoundClaimed("Alice", "Blue hat"),
                        new NotificationData.NotificationLink("lost-and-found")));
        tell(
                member,
                NotificationType.MEMBER_ADDED_TO_GROUP,
                NotificationData.of(
                        new NotificationParams.MemberAddedToGroup("Rescue Team", null),
                        new NotificationData.NotificationLink("dashboard-overview")));
        tell(
                member,
                NotificationType.PROCUREMENT_FULFILLED,
                NotificationData.of(
                        new NotificationParams.ProcurementFulfilled("Helmet"),
                        new NotificationData.NotificationLink("inventory-procurement")));
        tell(
                member,
                NotificationType.EVENT_REGISTRATION_STATUS,
                NotificationData.of(
                        new NotificationParams.EventRegistrationStatus(
                                "Tim Berger", "Marathon", RegistrationStatus.ACCEPTED, "Run 10km"),
                        new NotificationData.NotificationLink("events-registrations")));
        tell(
                member,
                NotificationType.NEWS_COMMENT,
                NotificationData.of(
                        new NotificationParams.NewsComment("Latest News", "Bob", "Great article!"),
                        new NotificationData.NotificationLink("news-list")));
        tell(
                member,
                NotificationType.LENDING_NEW_REQUEST,
                NotificationData.of(
                        new NotificationParams.LendingNewRequest("Partner Station", "Radio"),
                        new NotificationData.NotificationLink("lending-request", Map.of("id", 7))));
        tell(
                member,
                NotificationType.LENDING_STATUS_CHANGE,
                NotificationData.of(
                        new NotificationParams.LendingStatusChange("Partner", LendingStatus.DECLINED),
                        new NotificationData.NotificationLink("dashboard-overview")));
        tell(
                member,
                NotificationType.LENDING_NEW_MESSAGE,
                NotificationData.of(
                        new NotificationParams.LendingNewMessage("Partner", "Alice"),
                        new NotificationData.NotificationLink("dashboard-overview")));

        assertTrue(unread(member1.id()).size() >= 5);
    }

    @Test
    @Order(109)
    void aRestrictedNoticeReachesOnlyItsAudienceAndIsWithdrawnByItsLink() {
        var link = new NotificationData.NotificationLink("news-detail", Map.of("id", 777));
        var data = NotificationData.of(new NotificationParams.NewNews("Nur für dich", "Anna", "p"), link);

        tell(
                StationAudience.visibleTo(station.id(), Optional.of(Set.of(member2.id()))),
                NotificationType.NEW_NEWS,
                data);
        assertTrue(unread(member2.id()).stream().anyMatch(n -> n.data().equals(data)));
        assertTrue(unread(member1.id()).stream().noneMatch(n -> n.data().equals(data)));

        notifier.withdraw(NotificationType.NEW_NEWS, link);
        assertTrue(unread(member2.id()).stream().noneMatch(n -> n.data().equals(data)));
    }

    private static void tell(Audience audience, NotificationType type, NotificationData data) {
        notifier.notify(audience, type, data, Delivery.EVERY_TIME);
    }

    private static void tellOnce(Audience audience, NotificationType type, NotificationData data) {
        notifier.notify(audience, type, data, Delivery.ONCE_WHILE_UNREAD);
    }

    private static List<Notification> unread(int memberId) {
        return inbox.unread(Recipient.stationMember(memberId));
    }

    private static List<Notification> recent(int memberId) {
        return inbox.recent(Recipient.stationMember(memberId));
    }

    private static int countUnread(int memberId) {
        return inbox.countUnread(Recipient.stationMember(memberId));
    }

    private static void acknowledge(int id, int memberId) {
        inbox.acknowledge(Recipient.stationMember(memberId), id);
    }

    private static int acknowledgeAll(int memberId) {
        return inbox.acknowledgeAll(Recipient.stationMember(memberId));
    }

    private static List<Notification> clusterUnread(int clusterMemberId) {
        return inbox.unread(Recipient.clusterMember(clusterMemberId));
    }

    private static List<Notification> clusterRecent(int clusterMemberId) {
        return inbox.recent(Recipient.clusterMember(clusterMemberId));
    }

    private static int clusterCountUnread(int clusterMemberId) {
        return inbox.countUnread(Recipient.clusterMember(clusterMemberId));
    }

    private static void clusterAcknowledge(int id, int clusterMemberId) {
        inbox.acknowledge(Recipient.clusterMember(clusterMemberId), id);
    }

    private static int clusterAcknowledgeAll(int clusterMemberId) {
        return inbox.acknowledgeAll(Recipient.clusterMember(clusterMemberId));
    }
}
