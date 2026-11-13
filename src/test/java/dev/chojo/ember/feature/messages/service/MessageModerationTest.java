/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.messages.entity.ChatAudience;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageView;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Removing messages for a station, reporting them, and handling the reports. */
class MessageModerationTest extends MessageTestBase {
    private MessageFixtures services;
    private Station station;
    private Account moderator;
    private Account anna;
    private Account ben;
    private Conversation chat;

    @BeforeEach
    void setUp() {
        services = MessageFixtures.build();
        station = station();
        moderator = memberOf(station, "Mia");
        stationMemberRepo.setUserType(
                stationMemberRepo
                        .findByStationAndAccount(station.id(), moderator.id())
                        .orElseThrow()
                        .id(),
                StationUserType.MANAGER);
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
        chat = services.stationChats()
                .open(station.id(), moderator.id(), "Alle", ChatAudience.STATION, null, null, false);
    }

    @Test
    void aModeratorRemovesAnybodysMessageAndOnlyModeratorsSeeWho() {
        int id = send(anna, "Unfug");

        services.messages().delete(moderator.id(), chat.uid(), id);

        Message seenByBen = services.messages().one(ben.id(), chat.uid(), id).message();
        assertTrue(seenByBen.deleted());
        assertTrue(seenByBen.removedByStation());
        assertNull(seenByBen.deletedBy());
        assertEquals("", seenByBen.content());
        Message seenByModerator =
                services.messages().one(moderator.id(), chat.uid(), id).message();
        assertEquals(moderator.id(), seenByModerator.deletedBy().accountId());
    }

    @Test
    void anAuthorsOwnDeletionIsNoRemovalByTheStation() {
        int id = send(anna, "Vertippt");

        services.messages().delete(anna.id(), chat.uid(), id);

        Message deleted = services.messages().one(ben.id(), chat.uid(), id).message();
        assertTrue(deleted.deleted());
        assertFalse(deleted.removedByStation());
    }

    @Test
    void somebodyWithoutThePermissionDeletesOnlyTheirOwn() {
        int id = send(anna, "Hallo");

        assertRefused(MessageRefusal.DELETE_NOT_YOURS, () -> services.messages().delete(ben.id(), chat.uid(), id));
        int line = services.messages()
                .page(moderator.id(), chat.uid(), null, null)
                .messages()
                .getFirst()
                .message()
                .id();
        assertRefused(
                MessageRefusal.DELETE_NOT_YOURS, () -> services.messages().delete(moderator.id(), chat.uid(), line));
    }

    @Test
    void aModeratorHasNoSayInAPrivateGroup() {
        var group = services.groups().create(moderator.id(), List.of(anna.uid()), "Privat");
        int id = services.messages()
                .send(anna.id(), group.uid(), "Privat", null)
                .message()
                .id();

        assertRefused(
                MessageRefusal.DELETE_NOT_YOURS, () -> services.messages().delete(moderator.id(), group.uid(), id));
    }

    @Test
    void theDetailSaysWhetherTheReaderModerates() {
        assertTrue(services.conversations().detail(moderator.id(), chat.uid()).moderator());
        assertFalse(services.conversations().detail(anna.id(), chat.uid()).moderator());
    }

    @Test
    void aReportTellsTheModeratorsOnceAndIsListedWithItsContext() {
        for (int i = 0; i < 4; i++) send(ben, "Davor " + i);
        int id = send(anna, "Gemeldet");
        for (int i = 0; i < 4; i++) send(ben, "Danach " + i);

        services.reports().report(ben.id(), chat.uid(), id, "  Beleidigend  ");
        services.reports().report(ben.id(), chat.uid(), id, "Nochmal");

        verify(services.notifier(), times(1))
                .notify(any(), eq(NotificationType.MESSAGE_REPORTED), any(), eq(Delivery.ONCE_WHILE_UNREAD));
        var open = services.reports().open(station.id(), moderator.id());
        assertEquals(1, open.size());
        assertEquals("Beleidigend", open.getFirst().report().reason());
        assertEquals(ben.id(), open.getFirst().report().reporter().accountId());
        List<String> context = open.getFirst().context().stream()
                .map(MessageView::message)
                .map(Message::content)
                .toList();
        assertEquals(List.of("Davor 1", "Davor 2", "Davor 3", "Gemeldet", "Danach 0", "Danach 1", "Danach 2"), context);
        assertTrue(services.reports().open(station().id(), moderator.id()).isEmpty());
    }

    @Test
    void onlyOthersMessagesInAStationChatCanBeReported() {
        int own = send(ben, "Meins");
        int line = services.messages()
                .page(ben.id(), chat.uid(), null, null)
                .messages()
                .getFirst()
                .message()
                .id();
        var group = services.groups().create(ben.id(), List.of(anna.uid()), "Privat");
        int privat = services.messages()
                .send(anna.id(), group.uid(), "Privat", null)
                .message()
                .id();
        int other = send(anna, "Text");

        assertRefused(
                MessageRefusal.REPORT_OWN_MESSAGE, () -> services.reports().report(ben.id(), chat.uid(), own, null));
        assertRefused(
                MessageRefusal.REPORT_NOT_TAKEN, () -> services.reports().report(ben.id(), chat.uid(), line, null));
        assertRefused(MessageRefusal.REPORT_NOT_A_STATION_CHAT, () -> services.reports()
                .report(ben.id(), group.uid(), privat, null));
        assertRefused(MessageRefusal.REPORT_REASON_TOO_LONG, () -> services.reports()
                .report(ben.id(), chat.uid(), other, "x".repeat(501)));
        verify(services.notifier(), never()).notify(any(), any(), any(), any());
    }

    @Test
    void removingAReportedMessageHandlesEveryReportOfIt() {
        int id = send(anna, "Gemeldet");
        services.reports().report(ben.id(), chat.uid(), id, null);
        services.reports().report(moderator.id(), chat.uid(), id, null);
        int reportId = services.reports()
                .open(station.id(), moderator.id())
                .getFirst()
                .report()
                .id();

        services.reports().remove(station.id(), moderator.id(), reportId);

        assertTrue(services.messages().one(ben.id(), chat.uid(), id).message().removedByStation());
        assertTrue(services.reports().open(station.id(), moderator.id()).isEmpty());
        assertRefused(MessageRefusal.REPORT_NOT_HERE, () -> services.reports()
                .dismiss(station.id(), moderator.id(), reportId));
    }

    @Test
    void dismissingLeavesTheMessageAndRemovingInTheChatHandlesTheReportsToo() {
        int kept = send(anna, "Harmlos");
        int removed = send(anna, "Schlimm");
        services.reports().report(ben.id(), chat.uid(), kept, null);
        services.reports().report(ben.id(), chat.uid(), removed, null);
        int keptReport = services.reports()
                .open(station.id(), moderator.id())
                .getFirst()
                .report()
                .id();

        services.reports().dismiss(station.id(), moderator.id(), keptReport);
        services.messages().delete(moderator.id(), chat.uid(), removed);

        assertFalse(
                services.messages().one(ben.id(), chat.uid(), kept).message().deleted());
        assertTrue(services.reports().open(station.id(), moderator.id()).isEmpty());
        assertRefused(MessageRefusal.REPORT_NOT_HERE, () -> services.reports()
                .remove(station().id(), moderator.id(), keptReport));
    }

    @Test
    void aReportOfAnotherStationIsNotHere() {
        int id = send(anna, "Gemeldet");
        services.reports().report(ben.id(), chat.uid(), id, null);
        int reportId = services.reports()
                .open(station.id(), moderator.id())
                .getFirst()
                .report()
                .id();

        assertRefused(MessageRefusal.REPORT_NOT_HERE, () -> services.reports()
                .remove(station().id(), moderator.id(), reportId));
    }

    @Test
    void theRightAtAnotherStationOrAsAFormerMemberCountsForNothing() {
        Station other = station();
        var visitor = memberOf(station, "Vera");
        var atOther = join(other, visitor);
        stationMemberRepo.setUserType(atOther.id(), StationUserType.MANAGER);
        services.membership().reconcile(station.id());
        int id = send(anna, "Hallo");

        assertRefused(MessageRefusal.DELETE_NOT_YOURS, () -> services.messages().delete(visitor.id(), chat.uid(), id));
        assertFalse(services.conversations().detail(visitor.id(), chat.uid()).moderator());

        stationMemberRepo.setFormer(memberOf(moderator).id(), true);
        assertFalse(services.moderation().moderates(moderator.id(), chat));
    }

    @Test
    void aPageHidesTheRemoverFromThoseWhoDoNotModerate() {
        int id = send(anna, "Unfug");
        services.messages().delete(moderator.id(), chat.uid(), id);

        Message onBensPage = services.messages().page(ben.id(), chat.uid(), null, null).messages().stream()
                .map(MessageView::message)
                .filter(message -> message.id() == id)
                .findFirst()
                .orElseThrow();

        assertTrue(onBensPage.removedByStation());
        assertNull(onBensPage.deletedBy());
    }

    @Test
    void aRemovedMessageCannotBeEditedBack() {
        int id = send(anna, "Unfug");
        services.messages().delete(moderator.id(), chat.uid(), id);

        assertRefused(MessageRefusal.EDIT_DELETED, () -> services.messages().edit(anna.id(), chat.uid(), id, "Doch"));
        assertEquals(
                "", services.messages().one(ben.id(), chat.uid(), id).message().content());
    }

    @Test
    void anAuthorDeletingTheirReportedMessageClosesItsReports() {
        int id = send(anna, "Gemeldet");
        services.reports().report(ben.id(), chat.uid(), id, null);

        services.messages().delete(anna.id(), chat.uid(), id);

        assertTrue(services.reports().open(station.id(), moderator.id()).isEmpty());
    }

    @Test
    void aReportKeepsTheTextAsItWasReported() {
        int id = send(anna, "Beleidigung");
        services.reports().report(ben.id(), chat.uid(), id, null);

        services.messages().edit(anna.id(), chat.uid(), id, "ok");

        assertEquals(
                "Beleidigung",
                services.reports()
                        .open(station.id(), moderator.id())
                        .getFirst()
                        .report()
                        .content());
    }

    @Test
    void theReporterIsNotToldAboutTheirOwnReport() {
        int id = send(anna, "Gemeldet");

        services.reports().report(moderator.id(), chat.uid(), id, null);

        verify(services.notifier())
                .notify(
                        eq(StationAudience.holders(station.id(), StationPermission.MESSAGE_CHAT_MANAGE)
                                .except(memberOf(moderator).id())),
                        eq(NotificationType.MESSAGE_REPORTED),
                        any(),
                        any());
    }

    private StationMember memberOf(Account account) {
        return stationMemberRepo
                .findByStationAndAccount(station.id(), account.id())
                .orElseThrow();
    }

    private int send(Account author, String text) {
        return services.messages()
                .send(author.id(), chat.uid(), text, null)
                .message()
                .id();
    }
}
