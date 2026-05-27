/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationServiceTest extends RepositoryTestBase {
    private static NotificationService service;
    private static Station station;
    private static Account account1;
    private static Account account2;
    private static StationMember member1;
    private static StationMember member2;

    @BeforeAll
    static void setup() {
        var emailService = mock(EmailService.class);
        var mailing = new Mailing();
        service = new NotificationService(
                notificationRepo,
                stationMemberRepo,
                userSettingsRepo,
                notificationSettingsRepo,
                accountRepo,
                stationRepo,
                emailService,
                mailing);

        station = stationRepo.create("NotifStation");
        account1 = accountRepo.create("notif1@test.com", "Notif", "One");
        account2 = accountRepo.create("notif2@test.com", "Notif", "Two");
        member1 = stationMemberRepo.create(station.id(), account1.id());
        member2 = stationMemberRepo.create(station.id(), account2.id());
        stationMemberRepo.findRoleByName(Roles.MEMBER).ifPresent(r -> {
            stationMemberRepo.addRole(member1.id(), r.id());
            stationMemberRepo.addRole(member2.id(), r.id());
        });
        stationMemberRepo.findRoleByName(Roles.LOGIN).ifPresent(r -> {
            stationMemberRepo.addRole(member1.id(), r.id());
            stationMemberRepo.addRole(member2.id(), r.id());
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
        var data = NotificationData.of(new NotificationParams.NewNews("Title", "Author", "Preview"));
        service.notify(member1.id(), NotificationType.NEW_NEWS, data);

        var unack = service.findUnacknowledged(member1.id());
        assertTrue(unack.stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
    }

    @Test
    @Order(2)
    void countUnacknowledged() {
        assertTrue(service.countUnacknowledged(member1.id()) >= 1);
        assertEquals(0, service.countUnacknowledged(member2.id()));
    }

    @Test
    @Order(3)
    void notifyStation() {
        var data = NotificationData.of(
                new NotificationParams.NewEvent("Test Event", "Description"),
                new NotificationData.NotificationLink("event-detail"));
        service.notifyStation(station.id(), NotificationType.NEW_EVENT, data);

        // Both members should receive it
        assertTrue(service.findUnacknowledged(member1.id()).stream()
                .anyMatch(n -> n.type() == NotificationType.NEW_EVENT));
        assertTrue(service.findUnacknowledged(member2.id()).stream()
                .anyMatch(n -> n.type() == NotificationType.NEW_EVENT));
    }

    @Test
    @Order(4)
    void acknowledgeReducesCount() {
        int before = service.countUnacknowledged(member1.id());
        var first = service.findUnacknowledged(member1.id()).getFirst();
        service.acknowledge(first.id(), member1.id());
        assertEquals(before - 1, service.countUnacknowledged(member1.id()));
    }

    @Test
    @Order(5)
    void acknowledgeAllClearsAll() {
        int count = service.acknowledgeAll(member2.id());
        assertTrue(count >= 1);
        assertEquals(0, service.countUnacknowledged(member2.id()));
    }

    @Test
    @Order(10)
    void notifyIfAbsentDoesNotDuplicate() {
        var data = NotificationData.of(new NotificationParams.ProcurementRequested("Helm"));
        service.notify(member1.id(), NotificationType.PROCUREMENT_REQUESTED, data);
        int before = service.findUnacknowledged(member1.id()).size();

        // Same notification again — should not duplicate
        service.notifyIfAbsent(member1.id(), NotificationType.PROCUREMENT_REQUESTED, data);
        int after = service.findUnacknowledged(member1.id()).size();
        assertEquals(before, after);
    }

    @Test
    @Order(20)
    void deleteByTypeContaining() {
        var data = NotificationData.of(new NotificationParams.LostAndFoundNew("Blue hat"));
        service.notify(member1.id(), NotificationType.LOST_AND_FOUND_NEW, data);
        assertTrue(service.findUnacknowledged(member1.id()).stream()
                .anyMatch(n -> n.type() == NotificationType.LOST_AND_FOUND_NEW));

        service.deleteByTypeContaining(
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(new NotificationParams.LostAndFoundNew("Blue hat"))
                        .toJson());

        assertFalse(service.findUnacknowledged(member1.id()).stream()
                .anyMatch(n -> n.type() == NotificationType.LOST_AND_FOUND_NEW));
    }

    @Test
    @Order(25)
    void findAll() {
        var all = service.findAll(member1.id());
        assertNotNull(all);
        assertFalse(all.isEmpty());
    }

    @Test
    @Order(26)
    void getNotificationSettings() {
        var settings = service.getNotificationSettings(member1.id());
        assertNotNull(settings);
    }

    @Test
    @Order(27)
    void cleanupOld() {
        assertDoesNotThrow(() -> service.cleanupOld());
    }

    @Test
    @Order(30)
    void notifyStationExcludesMember() {
        // Acknowledge all for both members first
        service.acknowledgeAll(member1.id());
        service.acknowledgeAll(member2.id());

        var data = NotificationData.of(new NotificationParams.NewNews("Excluded Test", "Author", "Preview"));
        service.notifyStation(station.id(), NotificationType.NEW_NEWS, data, member1.id());

        // member1 should be excluded
        assertFalse(
                service.findUnacknowledged(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        // member2 should receive it
        assertTrue(
                service.findUnacknowledged(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
    }

    @Test
    @Order(31)
    void notifyMembersWithRole() {
        service.acknowledgeAll(member1.id());
        service.acknowledgeAll(member2.id());

        var data = NotificationData.of(new NotificationParams.NewNews("Role Notify", "Author", "Preview"));
        service.notifyMembersWithRole(station.id(), "MEMBER", NotificationType.NEW_NEWS, data);

        // Both members have MEMBER role — both should receive
        assertTrue(
                service.findUnacknowledged(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        assertTrue(
                service.findUnacknowledged(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
    }

    @Test
    @Order(32)
    void notifyMembersWithRoleExcludesMember() {
        service.acknowledgeAll(member1.id());
        service.acknowledgeAll(member2.id());

        var data = NotificationData.of(new NotificationParams.NewNews("Role Exclude", "Author", "Preview"));
        service.notifyMembersWithRole(station.id(), "MEMBER", NotificationType.NEW_NEWS, data, member2.id());

        // member1 should receive
        assertTrue(
                service.findUnacknowledged(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        // member2 should be excluded
        assertFalse(
                service.findUnacknowledged(member2.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
    }

    @Test
    @Order(33)
    void notifyMembers() {
        service.acknowledgeAll(member1.id());
        service.acknowledgeAll(member2.id());

        var data = NotificationData.of(new NotificationParams.NewNews("Batch Notify", "Author", "Preview"));
        service.notifyMembers(List.of(member1.id(), member2.id()), NotificationType.NEW_NEWS, data);

        assertTrue(service.countUnacknowledged(member1.id()) >= 1);
        assertTrue(service.countUnacknowledged(member2.id()) >= 1);
    }

    @Test
    @Order(34)
    void notifyMembersIfAbsent() {
        service.acknowledgeAll(member1.id());
        service.acknowledgeAll(member2.id());

        var data = NotificationData.of(new NotificationParams.NewNews("IfAbsent", "Author", "Preview"));
        service.notifyMembersIfAbsent(
                List.of(member1.id(), member2.id()), NotificationType.NEW_NEWS, data, member2.id());

        // member1 should receive, member2 excluded
        assertTrue(
                service.findUnacknowledged(member1.id()).stream().anyMatch(n -> n.type() == NotificationType.NEW_NEWS));
        assertEquals(0, service.countUnacknowledged(member2.id()));

        // Calling again should not duplicate for member1
        int before = service.findUnacknowledged(member1.id()).size();
        service.notifyMembersIfAbsent(List.of(member1.id()), NotificationType.NEW_NEWS, data, -1);
        int after = service.findUnacknowledged(member1.id()).size();
        assertEquals(before, after);
    }

    // -- Digest: processDigest with email disabled (no user settings) --

    @Test
    @Order(40)
    void processDigestNoUnemailed() {
        // No unemailed notifications — digest should be a no-op
        assertDoesNotThrow(() -> invokeProcessDigest(service));
    }

    @Test
    @Order(41)
    void processDigestEmailDisabledUserSettings() {
        // Create notification, but user settings have email disabled (default)
        var data = NotificationData.of(new NotificationParams.NewEvent("Digest Test", "A test event"));
        service.notify(member1.id(), NotificationType.NEW_EVENT, data);

        // user settings not set — findByMemberId returns empty → trySendDigest returns false
        // but digest still marks as emailed to avoid retry
        assertDoesNotThrow(() -> invokeProcessDigest(service));
    }

    @Test
    @Order(42)
    void processDigestWithEmailEnabled() {
        // Set up a notification service with mocked EmailService that can send
        var emailServiceMock = mock(EmailService.class);
        when(emailServiceMock.getBaseUrl()).thenReturn("https://ember.example.com");
        when(emailServiceMock.canStationSend(anyInt())).thenReturn(true);
        when(emailServiceMock.loadTemplate(anyString(), anyString(), any())).thenReturn("<html>digest</html>");

        var mailing = new Mailing();
        var svc = new NotificationService(
                notificationRepo,
                stationMemberRepo,
                userSettingsRepo,
                notificationSettingsRepo,
                accountRepo,
                stationRepo,
                emailServiceMock,
                mailing);

        // Enable email for member1
        userSettingsRepo.updateEmailEnabled(member1.id(), true);

        // Enable email for a notification type
        notificationSettingsRepo.upsert(member1.id(), NotificationType.NEWS_COMMENT, true, true, false);

        // Create the notification so it shows up as unemailed
        var data =
                NotificationData.of(new NotificationParams.NewsComment("Breaking News", "Author", "Comment preview"));
        svc.notify(member1.id(), NotificationType.NEWS_COMMENT, data);

        // Run digest — should attempt to send email
        assertDoesNotThrow(() -> invokeProcessDigest(svc));

        // Disable email again to clean up
        userSettingsRepo.updateEmailEnabled(member1.id(), false);
    }

    @Test
    @Order(43)
    void processDigestWithNoLogoUrl() {
        // Verify digest works when station has no logo
        var emailServiceMock = mock(EmailService.class);
        when(emailServiceMock.getBaseUrl()).thenReturn("https://ember.example.com");
        when(emailServiceMock.canStationSend(anyInt())).thenReturn(true);
        when(emailServiceMock.loadTemplate(anyString(), anyString(), any())).thenReturn("<html>ok</html>");

        var mailing = new Mailing();
        var svc = new NotificationService(
                notificationRepo,
                stationMemberRepo,
                userSettingsRepo,
                notificationSettingsRepo,
                accountRepo,
                stationRepo,
                emailServiceMock,
                mailing);

        userSettingsRepo.updateEmailEnabled(member2.id(), true);
        notificationSettingsRepo.upsert(member2.id(), NotificationType.NEW_FORM, true, true, false);

        var data = NotificationData.of(new NotificationParams.NewForm("Feedback Form"));
        svc.notify(member2.id(), NotificationType.NEW_FORM, data);

        assertDoesNotThrow(() -> invokeProcessDigest(svc));

        userSettingsRepo.updateEmailEnabled(member2.id(), false);
    }

    // -- Notify: app notifications disabled --

    @Test
    @Order(50)
    void notifyReturnsNullWhenAppDisabled() {
        // Disable app notifications for member1 for WAITLIST_NEW_ENTRY
        notificationSettingsRepo.upsert(member1.id(), NotificationType.WAITLIST_NEW_ENTRY, false, false, false);
        var data = NotificationData.of(new NotificationParams.WaitlistNewEntry("Child", "List A"));
        var result = service.notify(member1.id(), NotificationType.WAITLIST_NEW_ENTRY, data);
        assertNull(result);
    }

    @Test
    @Order(51)
    void notifyIfAbsentSkipsWhenAppDisabled() {
        notificationSettingsRepo.upsert(member2.id(), NotificationType.LENDING_NEW_REQUEST, false, false, false);
        int before = service.countUnacknowledged(member2.id());
        var data = NotificationData.of(new NotificationParams.LendingNewRequest("Station X", "Helmet"));
        service.notifyIfAbsent(member2.id(), NotificationType.LENDING_NEW_REQUEST, data);
        assertEquals(before, service.countUnacknowledged(member2.id()));
    }

    @Test
    @Order(52)
    void notifyMembersSkipsWhenAppDisabled() {
        notificationSettingsRepo.upsert(member1.id(), NotificationType.LENDING_STATUS_CHANGE, false, false, false);
        service.acknowledgeAll(member1.id());
        var data = NotificationData.of(new NotificationParams.LendingStatusChange("Station Y", "approved"));
        service.notifyMembers(List.of(member1.id()), NotificationType.LENDING_STATUS_CHANGE, data);
        // member1 has app disabled — no new notification
        assertFalse(service.findUnacknowledged(member1.id()).stream()
                .anyMatch(n -> n.type() == NotificationType.LENDING_STATUS_CHANGE));
    }

    @Test
    @Order(53)
    void notifyStationSkipsWhenAppDisabled() {
        notificationSettingsRepo.upsert(member2.id(), NotificationType.LENDING_NEW_MESSAGE, false, false, false);
        service.acknowledgeAll(member2.id());
        var data = NotificationData.of(new NotificationParams.LendingNewMessage("Station Z", "Alice"));
        service.notifyStation(station.id(), NotificationType.LENDING_NEW_MESSAGE, data);
        assertFalse(service.findUnacknowledged(member2.id()).stream()
                .anyMatch(n -> n.type() == NotificationType.LENDING_NEW_MESSAGE));
    }

    // -- All notification types: verify they create notifications --

    @Test
    @Order(60)
    void notifyAllTypesCreatesNotifications() {
        service.acknowledgeAll(member1.id());
        // Exchange types
        var exchStatus = NotificationData.of(
                new NotificationParams.ExchangeStatusChange("approved", "Helmet", "all good"),
                new NotificationData.NotificationLink("inventory-exchanges"));
        service.notify(member1.id(), NotificationType.EXCHANGE_STATUS_CHANGE, exchStatus);

        var exchReq = NotificationData.of(
                new NotificationParams.ExchangeNewRequest("Alice", "Helmet", "I need it"),
                new NotificationData.NotificationLink("inventory-exchanges"));
        service.notify(member1.id(), NotificationType.EXCHANGE_NEW_REQUEST, exchReq);

        var profChange = NotificationData.of(
                new NotificationParams.ProfileFieldChanged("Alice", "Phone"),
                new NotificationData.NotificationLink("members-detail", Map.of("id", 42)));
        service.notify(member1.id(), NotificationType.PROFILE_FIELD_CHANGED, profChange);

        var lostFound = NotificationData.of(
                new NotificationParams.LostAndFoundClaimed("Alice", "Blue hat"),
                new NotificationData.NotificationLink("lost-and-found"));
        service.notify(member1.id(), NotificationType.LOST_AND_FOUND_CLAIMED, lostFound);

        var memberGroup = NotificationData.of(
                new NotificationParams.MemberAddedToGroup("Rescue Team"),
                new NotificationData.NotificationLink("dashboard-overview"));
        service.notify(member1.id(), NotificationType.MEMBER_ADDED_TO_GROUP, memberGroup);

        var procFulfilled = NotificationData.of(
                new NotificationParams.ProcurementFulfilled("Helmet"),
                new NotificationData.NotificationLink("inventory-procurement"));
        service.notify(member1.id(), NotificationType.PROCUREMENT_FULFILLED, procFulfilled);

        var evtReg = NotificationData.of(
                new NotificationParams.EventRegistrationStatus("Marathon", "ACCEPTED", "Run 10km"),
                new NotificationData.NotificationLink("events-registrations"));
        service.notify(member1.id(), NotificationType.EVENT_REGISTRATION_STATUS, evtReg);

        var newsComment = NotificationData.of(
                new NotificationParams.NewsComment("Latest News", "Bob", "Great article!"),
                new NotificationData.NotificationLink("news-list"));
        service.notify(member1.id(), NotificationType.NEWS_COMMENT, newsComment);

        var lendReq = NotificationData.of(
                new NotificationParams.LendingNewRequest("Partner Station", "Radio"),
                new NotificationData.NotificationLink("lending-request", Map.of("id", 7)));
        service.notify(member1.id(), NotificationType.LENDING_NEW_REQUEST, lendReq);

        var lendStatus = NotificationData.of(new NotificationParams.LendingStatusChange("Partner", "denied"));
        service.notify(member1.id(), NotificationType.LENDING_STATUS_CHANGE, lendStatus);

        var lendMsg = NotificationData.of(new NotificationParams.LendingNewMessage("Partner", "Alice"));
        service.notify(member1.id(), NotificationType.LENDING_NEW_MESSAGE, lendMsg);

        var unack = service.findUnacknowledged(member1.id());
        assertTrue(unack.size() >= 5);
    }

    @Test
    @Order(61)
    void resolveNotificationUrlUnknownRoute() {
        // Notification with a link to an unknown route — should fall back to dashboard
        var emailServiceMock = mock(EmailService.class);
        when(emailServiceMock.getBaseUrl()).thenReturn("https://ember.example.com");
        when(emailServiceMock.canStationSend(anyInt())).thenReturn(true);
        when(emailServiceMock.loadTemplate(anyString(), anyString(), any())).thenReturn("<html>ok</html>");

        var mailing = new Mailing();
        var svc = new NotificationService(
                notificationRepo,
                stationMemberRepo,
                userSettingsRepo,
                notificationSettingsRepo,
                accountRepo,
                stationRepo,
                emailServiceMock,
                mailing);

        userSettingsRepo.updateEmailEnabled(member1.id(), true);
        notificationSettingsRepo.upsert(member1.id(), NotificationType.MEMBER_ADDED_TO_GROUP, true, true, false);

        // Create notification with unknown route
        var data = NotificationData.of(
                new NotificationParams.MemberAddedToGroup("Alpha Team"),
                new NotificationData.NotificationLink("unknown-route"));
        svc.notify(member1.id(), NotificationType.MEMBER_ADDED_TO_GROUP, data);

        assertDoesNotThrow(() -> invokeProcessDigest(svc));

        userSettingsRepo.updateEmailEnabled(member1.id(), false);
    }

    @Test
    @Order(62)
    void processDigestHandlesExceptionGracefully() {
        // Use a service where emailService throws on loadTemplate — digest should not propagate exception
        var emailServiceMock = mock(EmailService.class);
        when(emailServiceMock.getBaseUrl()).thenReturn("https://ember.example.com");
        when(emailServiceMock.canStationSend(anyInt())).thenReturn(true);
        when(emailServiceMock.loadTemplate(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Template error"));

        var mailing = new Mailing();
        var svc = new NotificationService(
                notificationRepo,
                stationMemberRepo,
                userSettingsRepo,
                notificationSettingsRepo,
                accountRepo,
                stationRepo,
                emailServiceMock,
                mailing);

        userSettingsRepo.updateEmailEnabled(member2.id(), true);
        notificationSettingsRepo.upsert(member2.id(), NotificationType.PROCUREMENT_FULFILLED, true, true, false);

        var data = NotificationData.of(new NotificationParams.ProcurementFulfilled("Radio"));
        svc.notify(member2.id(), NotificationType.PROCUREMENT_FULFILLED, data);

        // Should not throw even if email sending fails
        assertDoesNotThrow(() -> invokeProcessDigest(svc));

        userSettingsRepo.updateEmailEnabled(member2.id(), false);
    }

    // -- Helper --

    private static void invokeProcessDigest(NotificationService svc) {
        try {
            Method m = NotificationService.class.getDeclaredMethod("processDigest");
            m.setAccessible(true);
            m.invoke(svc);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
