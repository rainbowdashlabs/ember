/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
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
        var data = NotificationData.of(
                new NotificationParams.ProcurementRequested("Helm"),
                new NotificationData.NotificationLink("dashboard-overview"));
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
        var data = NotificationData.of(
                new NotificationParams.LostAndFoundNew("Blue hat"),
                new NotificationData.NotificationLink("dashboard-overview"));
        service.notify(member1.id(), NotificationType.LOST_AND_FOUND_NEW, data);
        assertTrue(service.findUnacknowledged(member1.id()).stream()
                .anyMatch(n -> n.type() == NotificationType.LOST_AND_FOUND_NEW));

        service.deleteByTypeContaining(
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(
                                new NotificationParams.LostAndFoundNew("Blue hat"),
                                new NotificationData.NotificationLink("dashboard-overview"))
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

        var data = NotificationData.of(
                new NotificationParams.NewNews("Excluded Test", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
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

        var data = NotificationData.of(
                new NotificationParams.NewNews("Role Notify", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        service.notifyMembersWithRole(station.id(), "USER", NotificationType.NEW_NEWS, data);

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

        var data = NotificationData.of(
                new NotificationParams.NewNews("Role Exclude", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        service.notifyMembersWithRole(station.id(), "USER", NotificationType.NEW_NEWS, data, member2.id());

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

        var data = NotificationData.of(
                new NotificationParams.NewNews("Batch Notify", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        service.notifyMembers(List.of(member1.id(), member2.id()), NotificationType.NEW_NEWS, data);

        assertTrue(service.countUnacknowledged(member1.id()) >= 1);
        assertTrue(service.countUnacknowledged(member2.id()) >= 1);
    }

    @Test
    @Order(34)
    void notifyMembersIfAbsent() {
        service.acknowledgeAll(member1.id());
        service.acknowledgeAll(member2.id());

        var data = NotificationData.of(
                new NotificationParams.NewNews("IfAbsent", "Author", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
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
        var data = NotificationData.of(
                new NotificationParams.NewEvent("Digest Test", "A test event"),
                new NotificationData.NotificationLink("dashboard-overview"));
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
        var data = NotificationData.of(
                new NotificationParams.NewsComment("Breaking News", "Author", "Comment preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
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

        var data = NotificationData.of(
                new NotificationParams.NewForm("Feedback Form"),
                new NotificationData.NotificationLink("dashboard-overview"));
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
        var data = NotificationData.of(
                new NotificationParams.WaitlistNewEntry("Child", "List A"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var result = service.notify(member1.id(), NotificationType.WAITLIST_NEW_ENTRY, data);
        assertNull(result);
    }

    @Test
    @Order(51)
    void notifyIfAbsentSkipsWhenAppDisabled() {
        notificationSettingsRepo.upsert(member2.id(), NotificationType.LENDING_NEW_REQUEST, false, false, false);
        int before = service.countUnacknowledged(member2.id());
        var data = NotificationData.of(
                new NotificationParams.LendingNewRequest("Station X", "Helmet"),
                new NotificationData.NotificationLink("dashboard-overview"));
        service.notifyIfAbsent(member2.id(), NotificationType.LENDING_NEW_REQUEST, data);
        assertEquals(before, service.countUnacknowledged(member2.id()));
    }

    @Test
    @Order(52)
    void notifyMembersSkipsWhenAppDisabled() {
        notificationSettingsRepo.upsert(member1.id(), NotificationType.LENDING_STATUS_CHANGE, false, false, false);
        service.acknowledgeAll(member1.id());
        var data = NotificationData.of(
                new NotificationParams.LendingStatusChange("Station Y", LendingStatus.APPROVED),
                new NotificationData.NotificationLink("dashboard-overview"));
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
        var data = NotificationData.of(
                new NotificationParams.LendingNewMessage("Station Z", "Alice"),
                new NotificationData.NotificationLink("dashboard-overview"));
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
                new NotificationParams.ExchangeStatusChange(ExchangeStatus.RECEIVED, "Helmet", "all good"),
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
                new NotificationParams.MemberAddedToGroup("Rescue Team", null),
                new NotificationData.NotificationLink("dashboard-overview"));
        service.notify(member1.id(), NotificationType.MEMBER_ADDED_TO_GROUP, memberGroup);

        var procFulfilled = NotificationData.of(
                new NotificationParams.ProcurementFulfilled("Helmet"),
                new NotificationData.NotificationLink("inventory-procurement"));
        service.notify(member1.id(), NotificationType.PROCUREMENT_FULFILLED, procFulfilled);

        var evtReg = NotificationData.of(
                new NotificationParams.EventRegistrationStatus("Marathon", RegistrationStatus.ACCEPTED, "Run 10km"),
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

        var lendStatus = NotificationData.of(
                new NotificationParams.LendingStatusChange("Partner", LendingStatus.DECLINED),
                new NotificationData.NotificationLink("dashboard-overview"));
        service.notify(member1.id(), NotificationType.LENDING_STATUS_CHANGE, lendStatus);

        var lendMsg = NotificationData.of(
                new NotificationParams.LendingNewMessage("Partner", "Alice"),
                new NotificationData.NotificationLink("dashboard-overview"));
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
                new NotificationParams.MemberAddedToGroup("Alpha Team", null),
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

        var data = NotificationData.of(
                new NotificationParams.ProcurementFulfilled("Radio"),
                new NotificationData.NotificationLink("dashboard-overview"));
        svc.notify(member2.id(), NotificationType.PROCUREMENT_FULFILLED, data);

        // Should not throw even if email sending fails
        assertDoesNotThrow(() -> invokeProcessDigest(svc));

        userSettingsRepo.updateEmailEnabled(member2.id(), false);
    }

    // -- Localisation / feed body resolvers --

    @Test
    @Order(100)
    void resolveLocaleHandlesGermanEnglishAndNull() {
        assertEquals("de", service.resolveLocale("de-DE"));
        assertEquals("de", service.resolveLocale("de"));
        assertEquals("en", service.resolveLocale("en-US"));
        assertEquals("en", service.resolveLocale(null));
        assertEquals("en", service.resolveLocale("fr-FR"));
    }

    @Test
    @Order(101)
    void resolveLocalizedSubstitutesParamsAndFallsBackToKey() {
        String body = service.resolveLocalized("de", "feed", "title", Map.of("stationName", "Demo"));
        assertTrue(body.contains("Demo"));

        // Unknown key returns the key itself.
        assertEquals("missing.key", service.resolveLocalized("de", "feed", "missing.key", null));
    }

    @Test
    @Order(102)
    void resolveCategoryReturnsLocalisedLabelAndFallsBackToEnumName() {
        assertEquals("Neuigkeit", service.resolveCategory("de", NotificationType.NEW_NEWS));
        assertEquals("News", service.resolveCategory("en", NotificationType.NEW_NEWS));
        // STORAGE_WARNING was added in the i18n completion pass — verify it's now localised.
        assertEquals("Speicherwarnung", service.resolveCategory("de", NotificationType.STORAGE_WARNING));
        assertEquals("Storage Warning", service.resolveCategory("en", NotificationType.STORAGE_WARNING));
    }

    @Test
    @Order(103)
    void resolveMessageSubstitutesParamsAndFallsBackToJoinedParams() {
        var data = NotificationData.of(
                new NotificationParams.NewEvent("Sprechstunde", "Etwas Beschreibung"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var notif = new dev.chojo.ember.feature.notifications.entity.Notification(
                1, member1.id(), NotificationType.NEW_EVENT, data, java.time.Instant.now(), null);
        String msg = service.resolveMessage("de", notif);
        assertTrue(msg.contains("Sprechstunde"));

        // Type without translation: STORAGE_WARNING — should join params.
        var storageData = NotificationData.of(
                new NotificationParams.StorageWarning(95, "9.5 GB", "10 GB"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var storageNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                2, member1.id(), NotificationType.STORAGE_WARNING, storageData, java.time.Instant.now(), null);
        String storageMsg = service.resolveMessage("de", storageNotif);
        assertTrue(storageMsg.contains("95"));
    }

    @Test
    @Order(104)
    void resolveDetailReturnsTypeSpecificStringOrNull() {
        var data = NotificationData.of(
                new NotificationParams.NewNews("Titel", "Autor", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var notif = new dev.chojo.ember.feature.notifications.entity.Notification(
                3, member1.id(), NotificationType.NEW_NEWS, data, java.time.Instant.now(), null);
        assertEquals("Preview", service.resolveDetail(notif));

        var none = NotificationData.of(
                new NotificationParams.MemberAddedToGroup("Alpha", null),
                new NotificationData.NotificationLink("dashboard-overview"));
        var noneNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                4, member1.id(), NotificationType.MEMBER_ADDED_TO_GROUP, none, java.time.Instant.now(), null);
        assertNull(service.resolveDetail(noneNotif));
    }

    @Test
    @Order(105)
    void resolveFeedBodyRendersRichTypeSpecificMultiLineBody() {
        // NEW_EVENT — message + eventDescription
        var ne = NotificationData.of(
                new NotificationParams.NewEvent("Probe", "Wir üben für das Konzert"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var neNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                10, member1.id(), NotificationType.NEW_EVENT, ne, java.time.Instant.now(), null);
        String neBody = service.resolveFeedBody("de", neNotif);
        assertTrue(neBody.contains("Probe"));
        assertTrue(neBody.contains("Wir üben"));

        // NEW_EVENTS_BATCH — count + preview as labeled line
        var batchData = NotificationData.of(
                new NotificationParams.NewEventsBatch(3, "A, B, C", null),
                new NotificationData.NotificationLink("dashboard-overview"));
        var batchNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                11, member1.id(), NotificationType.NEW_EVENTS_BATCH, batchData, java.time.Instant.now(), null);
        String batchBody = service.resolveFeedBody("de", batchNotif);
        assertTrue(batchBody.contains("3"));
        assertTrue(batchBody.contains("A, B, C"));
        assertTrue(batchBody.contains("Termine"));

        // BOARD_TICKET_UPDATE — change description + ticket key + board labels
        var btu = NotificationData.of(
                new NotificationParams.BoardTicketUpdate("Vorstand", "VORSTAND-12", "Status changed"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var btuNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                12, member1.id(), NotificationType.BOARD_TICKET_UPDATE, btu, java.time.Instant.now(), null);
        String btuBody = service.resolveFeedBody("en", btuNotif);
        assertTrue(btuBody.contains("VORSTAND-12"));
        assertTrue(btuBody.contains("Vorstand"));

        // STORAGE_WARNING — labelled lines for usedPercent/used/quota
        var sw = NotificationData.of(
                new NotificationParams.StorageWarning(91, "9.1 GB", "10 GB"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var swNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                13, member1.id(), NotificationType.STORAGE_WARNING, sw, java.time.Instant.now(), null);
        String swBody = service.resolveFeedBody("en", swNotif);
        assertTrue(swBody.contains("91"));
        assertTrue(swBody.contains("9.1 GB"));
        assertTrue(swBody.contains("10 GB"));

        // REGISTRATION_DEADLINE_EXPIRED — labelled pendingCount
        var rde = NotificationData.of(
                new NotificationParams.RegistrationDeadlineExpired("Probe", 5),
                new NotificationData.NotificationLink("dashboard-overview"));
        var rdeNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                14, member1.id(), NotificationType.REGISTRATION_DEADLINE_EXPIRED, rde, java.time.Instant.now(), null);
        String rdeBody = service.resolveFeedBody("en", rdeNotif);
        assertTrue(rdeBody.contains("5"));
    }

    @Test
    @Order(106)
    void resolveNotificationUrlHandlesMissingLinkUnknownRouteAndKnownRoute() {
        // resolveNotificationUrl pre-dates the require-link guard. It still handles
        // legacy/malformed data that lacks a link — verify it short-circuits to null.
        var noLink = new NotificationData(new NotificationParams.MemberAddedToGroup("Alpha", null), null);
        assertNull(service.resolveNotificationUrl("https://ember.example.com", noLink));

        var unknown = NotificationData.of(
                new NotificationParams.MemberAddedToGroup("Alpha", null),
                new NotificationData.NotificationLink("bogus-route"));
        assertEquals(
                "https://ember.example.com/station/dashboard/overview",
                service.resolveNotificationUrl("https://ember.example.com", unknown));

        var known = NotificationData.of(
                new NotificationParams.NewEvent("Probe", ""),
                new NotificationData.NotificationLink("event-detail", Map.of("id", 42)));
        assertEquals(
                "https://ember.example.com/station/events/42",
                service.resolveNotificationUrl("https://ember.example.com", known));
    }

    @Test
    @Order(107)
    void resolveDetailCoversAllNonNullTypeBranches() {
        var nc = NotificationData.of(
                new NotificationParams.NewsComment("Titel", "Autor", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var ncNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                20, member1.id(), NotificationType.NEWS_COMMENT, nc, java.time.Instant.now(), null);
        assertEquals("Preview", service.resolveDetail(ncNotif));

        var esc = NotificationData.of(
                new NotificationParams.ExchangeStatusChange(ExchangeStatus.EXCHANGED, "Inv", "Reason"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var escNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                21, member1.id(), NotificationType.EXCHANGE_STATUS_CHANGE, esc, java.time.Instant.now(), null);
        assertEquals("Reason", service.resolveDetail(escNotif));

        var enr = NotificationData.of(
                new NotificationParams.ExchangeNewRequest("Name", "Inv", "Why"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var enrNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                22, member1.id(), NotificationType.EXCHANGE_NEW_REQUEST, enr, java.time.Instant.now(), null);
        assertEquals("Why", service.resolveDetail(enrNotif));

        var ers = NotificationData.of(
                new NotificationParams.EventRegistrationStatus("Probe", RegistrationStatus.ACCEPTED, "Konzert"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var ersNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                23, member1.id(), NotificationType.EVENT_REGISTRATION_STATUS, ers, java.time.Instant.now(), null);
        assertEquals("Konzert", service.resolveDetail(ersNotif));

        var ne = NotificationData.of(
                new NotificationParams.NewEvent("Probe", "Konzert"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var neNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                24, member1.id(), NotificationType.NEW_EVENT, ne, java.time.Instant.now(), null);
        assertEquals("Konzert", service.resolveDetail(neNotif));
    }

    @Test
    @Order(108)
    void resolveFeedBodyCoversAllRemainingTypes() {
        // NEW_NEWS — appends preview
        var news = NotificationData.of(
                new NotificationParams.NewNews("Titel", "Autor", "Preview"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var newsNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                30, member1.id(), NotificationType.NEW_NEWS, news, java.time.Instant.now(), null);
        assertTrue(service.resolveFeedBody("de", newsNotif).contains("Preview"));

        // NEWS_COMMENT — appends preview
        var nc = NotificationData.of(
                new NotificationParams.NewsComment("Titel", "Autor", "Kommentartext"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var ncNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                31, member1.id(), NotificationType.NEWS_COMMENT, nc, java.time.Instant.now(), null);
        assertTrue(service.resolveFeedBody("de", ncNotif).contains("Kommentartext"));

        // EVENT_REGISTRATION_STATUS — appends event description
        var ers = NotificationData.of(
                new NotificationParams.EventRegistrationStatus("Probe", RegistrationStatus.ACCEPTED, "Konzertprobe"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var ersNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                32, member1.id(), NotificationType.EVENT_REGISTRATION_STATUS, ers, java.time.Instant.now(), null);
        assertTrue(service.resolveFeedBody("en", ersNotif).contains("Konzertprobe"));

        // EVENT_CANCELLED — labelled reason
        var ec = NotificationData.of(
                new NotificationParams.EventCancelled("Probe", "Wetter"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var ecNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                33, member1.id(), NotificationType.EVENT_CANCELLED, ec, java.time.Instant.now(), null);
        var ecBody = service.resolveFeedBody("en", ecNotif);
        assertTrue(ecBody.contains("Wetter"));

        // EVENT_REMINDER — labelled eventDate + daysBefore
        var er = NotificationData.of(
                new NotificationParams.EventReminder("Probe", 3, java.time.LocalDate.parse("2026-08-01")),
                new NotificationData.NotificationLink("dashboard-overview"));
        var erNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                34, member1.id(), NotificationType.EVENT_REMINDER, er, java.time.Instant.now(), null);
        var erBody = service.resolveFeedBody("en", erNotif);
        assertTrue(erBody.contains("2026-08-01"));
        assertTrue(erBody.contains("3"));

        // EXCHANGE_NEW_REQUEST — labelled reason
        var enr = NotificationData.of(
                new NotificationParams.ExchangeNewRequest("Name", "Inv", "Need it"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var enrNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                35, member1.id(), NotificationType.EXCHANGE_NEW_REQUEST, enr, java.time.Instant.now(), null);
        assertTrue(service.resolveFeedBody("en", enrNotif).contains("Need it"));

        // EXCHANGE_STATUS_CHANGE — labelled reason
        var esc = NotificationData.of(
                new NotificationParams.ExchangeStatusChange(ExchangeStatus.EXCHANGED, "Inv", "Approved"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var escNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                36, member1.id(), NotificationType.EXCHANGE_STATUS_CHANGE, esc, java.time.Instant.now(), null);
        assertTrue(service.resolveFeedBody("en", escNotif).contains("Approved"));

        // LOST_AND_FOUND_NEW — appends description
        var lf = NotificationData.of(
                new NotificationParams.LostAndFoundNew("Blue jacket"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var lfNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                37, member1.id(), NotificationType.LOST_AND_FOUND_NEW, lf, java.time.Instant.now(), null);
        assertTrue(service.resolveFeedBody("en", lfNotif).contains("Blue jacket"));

        // LENDING_NEW_REQUEST — labelled itemSummary
        var lnr = NotificationData.of(
                new NotificationParams.LendingNewRequest("Station", "Drum kit"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var lnrNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                38, member1.id(), NotificationType.LENDING_NEW_REQUEST, lnr, java.time.Instant.now(), null);
        assertTrue(service.resolveFeedBody("en", lnrNotif).contains("Drum kit"));

        // PROCEDURE_ITEM_CHECKED — labelled item + by
        var pic = NotificationData.of(
                new NotificationParams.ProcedureItemCheckedParams("Proc", "Item A", "Alice"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var picNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                39, member1.id(), NotificationType.PROCEDURE_ITEM_CHECKED, pic, java.time.Instant.now(), null);
        var picBody = service.resolveFeedBody("en", picNotif);
        assertTrue(picBody.contains("Item A"));
        assertTrue(picBody.contains("Alice"));

        // Default branch via type without specific feed handling — falls back to resolveDetail
        var nf = NotificationData.of(
                new NotificationParams.NewForm("Application"),
                new NotificationData.NotificationLink("dashboard-overview"));
        var nfNotif = new dev.chojo.ember.feature.notifications.entity.Notification(
                40, member1.id(), NotificationType.NEW_FORM, nf, java.time.Instant.now(), null);
        // resolveDetail returns null for NEW_FORM, so body is just the headline
        var nfBody = service.resolveFeedBody("en", nfNotif);
        assertFalse(nfBody.isBlank());
    }

    @Test
    @Order(108)
    void resolveMessagePicksPluralVariantBasedOnCount() {
        // newEventsBatch is the canonical pluralised type — the bundle defines .one and .other.
        var one = notificationWith(
                NotificationType.NEW_EVENTS_BATCH, new NotificationParams.NewEventsBatch(1, "A", null), 50);
        var many = notificationWith(
                NotificationType.NEW_EVENTS_BATCH, new NotificationParams.NewEventsBatch(3, "A, B, C", null), 51);
        // EN
        var enOne = service.resolveMessage("en", one);
        var enMany = service.resolveMessage("en", many);
        assertTrue(enOne.contains("1 new event"), "Singular variant should win when count == 1");
        assertFalse(enOne.contains("events were"), "Singular variant should not use plural noun");
        assertTrue(enMany.contains("3 new events"));
        // DE
        var deOne = service.resolveMessage("de", one);
        assertTrue(deOne.startsWith("Ein neuer Termin"), "DE singular variant: " + deOne);
    }

    @Test
    @Order(108)
    void resolveMessageFallsBackToSingularKeyWhenNoPluralDefined() {
        // newEvent has no .one/.other split — the lookup must still hit the bare key.
        var notif =
                notificationWith(NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Probe", "Konzert"), 52);
        var msg = service.resolveMessage("en", notif);
        assertTrue(msg.contains("Probe"));
    }

    private dev.chojo.ember.feature.notifications.entity.Notification notificationWith(
            NotificationType type, NotificationParams params, int id) {
        var data = NotificationData.of(params, new NotificationData.NotificationLink("dashboard-overview"));
        return new dev.chojo.ember.feature.notifications.entity.Notification(
                id, member1.id(), type, data, java.time.Instant.now(), null);
    }

    @Test
    @Order(108)
    void truncateSnippetCutsAtWordBoundaryAndAppendsEllipsis() {
        // Word-boundary cut: stays within budget, doesn't break mid-word.
        var truncated =
                NotificationService.truncateSnippet("The quick brown fox jumps over the lazy dog and runs further", 20);
        assertTrue(truncated.endsWith("\u2026"), "Should append ellipsis when truncating: " + truncated);
        assertTrue(truncated.length() <= 21, "Length should stay near cap: " + truncated);
        assertFalse(truncated.contains("jumps"), "Should cut before the next word");

        // Short string returned verbatim.
        assertEquals("hi", NotificationService.truncateSnippet("hi", 100));
        // Null passes through.
        assertNull(NotificationService.truncateSnippet(null, 10));
        // No space within budget — hard truncate.
        var hard = NotificationService.truncateSnippet("aaaaaaaaaaaaaaaaaaaa", 5);
        assertEquals("aaaaa\u2026", hard);
    }

    @Test
    @Order(108)
    void resolveFeedTitleEmbedsEntityIdentifierAndRoutesPlurals() {
        // Static entity title — singular template.
        var news = notificationWith(
                NotificationType.NEW_NEWS, new NotificationParams.NewNews("Q3 schedule", "Alice", "preview"), 200);
        var enTitle = service.resolveFeedTitle("en", news);
        assertTrue(enTitle.startsWith("News:"), "Expected EN category prefix in: " + enTitle);
        assertTrue(enTitle.contains("Q3 schedule"));
        var deTitle = service.resolveFeedTitle("de", news);
        assertTrue(deTitle.startsWith("Neuigkeit:"), "Expected DE category prefix in: " + deTitle);

        // Plural routing: count == 1 → .one
        var one = notificationWith(
                NotificationType.NEW_EVENTS_BATCH, new NotificationParams.NewEventsBatch(1, "A", null), 201);
        assertEquals("1 new event", service.resolveFeedTitle("en", one));
        // Plural routing: count > 1 → .other
        var many = notificationWith(
                NotificationType.NEW_EVENTS_BATCH, new NotificationParams.NewEventsBatch(3, "A, B, C", null), 202);
        assertEquals("3 new events", service.resolveFeedTitle("en", many));

        // Status-bearing types interpolate the {statusLabel} synthetic param.
        var reg = notificationWith(
                NotificationType.EVENT_REGISTRATION_STATUS,
                new NotificationParams.EventRegistrationStatus("Open Training", RegistrationStatus.ACCEPTED, "desc"),
                203);
        var regTitle = service.resolveFeedTitle("en", reg);
        assertTrue(regTitle.contains("Accepted"), "Should contain localised status: " + regTitle);
        assertTrue(regTitle.contains("\u2713"), "Should contain check-mark symbol: " + regTitle);
        assertTrue(regTitle.contains("Open Training"));

        // EVENT_REMINDER routes via daysBefore plural.
        var tomorrow = notificationWith(
                NotificationType.EVENT_REMINDER,
                new NotificationParams.EventReminder("Probe", 1, java.time.LocalDate.parse("2026-09-15")),
                204);
        assertTrue(service.resolveFeedTitle("en", tomorrow).contains("tomorrow"));
        var later = notificationWith(
                NotificationType.EVENT_REMINDER,
                new NotificationParams.EventReminder("Probe", 5, java.time.LocalDate.parse("2026-09-20")),
                205);
        assertTrue(service.resolveFeedTitle("en", later).contains("5 days"));
    }

    @Test
    @Order(108)
    void resolveFeedTitleFallsBackToCategoryWhenNoTemplateMatches() {
        // Synthesize a notification with a type but inject a malformed/empty params shape so
        // every placeholder strips out — the helper must fall back to the bare category rather
        // than render a broken "News: " row.
        var malformed = new dev.chojo.ember.feature.notifications.entity.Notification(
                300,
                member1.id(),
                NotificationType.NEW_NEWS,
                new NotificationData(new NotificationParams.NewNews(null, null, null), null),
                java.time.Instant.now(),
                null);
        var title = service.resolveFeedTitle("en", malformed);
        assertEquals("News", title, "Should fall back to bare category, not 'News: '");
    }

    @Test
    @Order(108)
    void resolveFeedTitleTruncatesOverlongFragments() {
        // 200-char article title must shrink to ≤ 80-char fragment, ending in ellipsis.
        String longTitle = "A".repeat(200);
        var n = notificationWith(
                NotificationType.NEW_NEWS, new NotificationParams.NewNews(longTitle, "Alice", "p"), 301);
        var title = service.resolveFeedTitle("en", n);
        assertTrue(
                title.endsWith("\u2026") || title.length() < longTitle.length(), "Title should be truncated: " + title);
        // Total title still reasonable.
        assertTrue(title.length() < 120, "Title shouldn't blow past the cap: " + title);
    }

    @Test
    @Order(108)
    void resolveStatusWithSymbolUsesLocalisedLabelAndIcon() {
        assertEquals("\u2713 Accepted", service.resolveStatusWithSymbol("en", "ACCEPTED"));
        assertEquals("\u2713 Angenommen", service.resolveStatusWithSymbol("de", "ACCEPTED"));
        // Unknown enum value: fall back to raw name with no symbol.
        assertEquals("MYSTERY", service.resolveStatusWithSymbol("en", "MYSTERY"));
        // Null guard.
        assertNull(service.resolveStatusWithSymbol("en", null));
    }

    @Test
    @Order(109)
    void notifyRejectsDataWithoutLink() {
        // Regression: every public notification creation must carry a NotificationLink so the
        // in-app view, email digest, and feed renderer all have a deep link to fall back on.
        var noLink = new NotificationData(new NotificationParams.NewNews("T", "A", "P"), null);
        assertThrows(
                IllegalArgumentException.class, () -> service.notify(member1.id(), NotificationType.NEW_NEWS, noLink));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.notifyIfAbsent(member1.id(), NotificationType.NEW_NEWS, noLink));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.notifyStation(station.id(), NotificationType.NEW_NEWS, noLink));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.notifyMembers(List.of(member1.id()), NotificationType.NEW_NEWS, noLink));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.notifyMembersIfAbsent(List.of(member1.id()), NotificationType.NEW_NEWS, noLink, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.notifyMembersWithRole(
                        station.id(), "STATION_MANAGER", NotificationType.NEW_NEWS, noLink));
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
