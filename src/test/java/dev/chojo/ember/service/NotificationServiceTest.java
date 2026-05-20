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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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
}
