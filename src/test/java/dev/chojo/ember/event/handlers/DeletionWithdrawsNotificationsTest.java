/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.event.events.FormDeleted;
import dev.chojo.ember.event.events.NewsDeleted;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * What a deleted thing takes with it, and what it leaves standing.
 *
 * <p>The second half of every case is the one that matters: another thing of the same kind keeps
 * its notification, which is what a withdrawal reaching too far would break.
 */
class DeletionWithdrawsNotificationsTest extends RepositoryTestBase {
    private static NotificationService notifications;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        notifications = new NotificationService(
                notificationRepo,
                stationMemberRepo,
                userSettingsRepo,
                notificationSettingsRepo,
                accountRepo,
                stationRepo,
                mock(StationLogoService.class),
                mock(EmailService.class),
                new MailRecipientService(accountRepo, stationMemberRepo),
                new Mailing());

        station = stationRepo.create("Withdrawal Station");
        account = accountRepo.create("withdrawal@test.com", "With", "Drawal");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void deletingAnArticleTakesEverythingWrittenAboutIt() {
        int announcement = create(
                NotificationType.NEW_NEWS,
                new NotificationParams.NewNews("Sturm", "Anna", "Es zog"),
                NotificationLinks.news(11));
        int comment = create(
                NotificationType.NEWS_COMMENT,
                new NotificationParams.NewsComment("Sturm", "Bea", "Danke"),
                NotificationLinks.news(11));
        int mention = create(
                NotificationType.COMMENT_MENTION,
                new NotificationParams.CommentMention("Sturm", "Bea", "@With"),
                NotificationLinks.news(11));
        notificationRepo.acknowledge(mention, member.id());
        int otherArticle = create(
                NotificationType.NEW_NEWS,
                new NotificationParams.NewNews("Sturm", "Anna", "Es zog"),
                NotificationLinks.news(12));

        new NewsDeletedHandler(notifications).handle(new NewsDeleted(station.id(), 11, "Sturm"));

        assertGone(announcement, comment, mention);
        assertStanding(otherArticle);
    }

    @Test
    void deletingAnAppointmentTakesItsAnnouncementAndItsReminders() {
        int announcement = create(
                NotificationType.NEW_EVENT,
                new NotificationParams.NewEvent("Probe", "Am Abend"),
                NotificationLinks.event(21));
        int answer = create(
                NotificationType.EVENT_CANCELLED,
                new NotificationParams.EventCancelled("Probe", "Krank"),
                NotificationLinks.event(21));
        int reminder = create(
                NotificationType.EVENT_REMINDER,
                new NotificationParams.EventReminder("Probe", 2, LocalDate.of(2026, 5, 4)),
                NotificationLinks.eventDate(21, LocalDate.of(2026, 5, 4)));
        notificationRepo.acknowledge(reminder, member.id());
        int otherAppointment = create(
                NotificationType.NEW_EVENT,
                new NotificationParams.NewEvent("Probe", "Am Abend"),
                NotificationLinks.event(22));
        int otherReminder = create(
                NotificationType.EVENT_REMINDER,
                new NotificationParams.EventReminder("Probe", 2, LocalDate.of(2026, 5, 4)),
                NotificationLinks.eventDate(22, LocalDate.of(2026, 5, 4)));

        new EventDeletedHandler(notifications).handle(new EventDeleted(station.id(), 21, "Probe"));

        assertGone(announcement, answer, reminder);
        assertStanding(otherAppointment, otherReminder);
    }

    @Test
    void deletingAFormTakesTheInvitationToFillIt() {
        int invitation = create(
                NotificationType.NEW_FORM, new NotificationParams.NewForm("Umfrage"), NotificationLinks.form(31));
        notificationRepo.acknowledge(invitation, member.id());
        int otherForm = create(
                NotificationType.NEW_FORM, new NotificationParams.NewForm("Umfrage"), NotificationLinks.form(32));

        new FormDeletedHandler(notifications).handle(new FormDeleted(station.id(), 31));

        assertGone(invitation);
        assertStanding(otherForm);
    }

    private static int create(NotificationType type, NotificationParams params, NotificationLink link) {
        return notificationRepo
                .create(member.id(), type, NotificationData.of(params, link))
                .id();
    }

    private static void assertGone(int... ids) {
        var left = remaining();
        for (int id : ids) {
            assertFalse(left.contains(id), "notification " + id + " should have been withdrawn");
        }
    }

    private static void assertStanding(int... ids) {
        var left = remaining();
        for (int id : ids) {
            assertTrue(left.contains(id), "notification " + id + " is about something else and should stand");
        }
    }

    private static List<Integer> remaining() {
        return notificationRepo.findAll(member.id()).stream()
                .map(Notification::id)
                .toList();
    }

    @Test
    void aLinkNamingAnotherEntityLeavesEverythingAlone() {
        int untouched = create(
                NotificationType.NEW_NEWS,
                new NotificationParams.NewNews("Nichts", null, null),
                NotificationLinks.news(99));

        assertEquals(0, notificationRepo.deleteAllPointingAt(NotificationLinks.news(98)));

        assertStanding(untouched);
    }
}
