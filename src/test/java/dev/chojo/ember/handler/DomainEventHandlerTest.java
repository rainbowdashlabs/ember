/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.handler;

import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.api.roles.StationUserType;
import dev.chojo.ember.event.events.BoardTicketChanged;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.event.events.ExchangeRequested;
import dev.chojo.ember.event.events.ExchangeStatusChanged;
import dev.chojo.ember.event.events.FormDeleted;
import dev.chojo.ember.event.events.FormPublished;
import dev.chojo.ember.event.events.LendingMessageSent;
import dev.chojo.ember.event.events.LendingRequested;
import dev.chojo.ember.event.events.LendingStatusChanged;
import dev.chojo.ember.event.events.MembersAddedToGroup;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.event.events.NewsDeleted;
import dev.chojo.ember.event.events.ProcurementCreated;
import dev.chojo.ember.event.events.ProcurementFulfilled;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.event.handlers.BoardTicketChangedHandler;
import dev.chojo.ember.event.handlers.CommentCreatedHandler;
import dev.chojo.ember.event.handlers.CommentDeletedHandler;
import dev.chojo.ember.event.handlers.EventCreatedHandler;
import dev.chojo.ember.event.handlers.EventDeletedHandler;
import dev.chojo.ember.event.handlers.EventRegistrationStatusHandler;
import dev.chojo.ember.event.handlers.ExchangeRequestedHandler;
import dev.chojo.ember.event.handlers.ExchangeStatusChangedHandler;
import dev.chojo.ember.event.handlers.FormDeletedHandler;
import dev.chojo.ember.event.handlers.FormPublishedHandler;
import dev.chojo.ember.event.handlers.LendingMessageSentHandler;
import dev.chojo.ember.event.handlers.LendingRequestedHandler;
import dev.chojo.ember.event.handlers.LendingStatusChangedHandler;
import dev.chojo.ember.event.handlers.MembersAddedToGroupHandler;
import dev.chojo.ember.event.handlers.MentionedInCommentHandler;
import dev.chojo.ember.event.handlers.NewsCreatedHandler;
import dev.chojo.ember.event.handlers.NewsDeletedHandler;
import dev.chojo.ember.event.handlers.ProcurementCreatedHandler;
import dev.chojo.ember.event.handlers.ProcurementFulfilledHandler;
import dev.chojo.ember.event.handlers.RegistrationDeadlineExpiredHandler;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomainEventHandlerTest {
    private NotificationService notificationService;
    private StationMemberRepository memberRepository;

    private static final int STATION_ID = 1;
    private static final int MEMBER_ID = 10;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        memberRepository = mock(StationMemberRepository.class);
    }

    private StationMember member(int id) {
        return new StationMember(id, STATION_ID, UUID.randomUUID(), id, false, null, "Member " + id, StationUserType.MEMBER);
    }

    // -- EventCreatedHandler --

    @Test
    void eventCreatedNotifiesStation() {
        var handler = new EventCreatedHandler(notificationService);
        assertEquals(EventCreated.class, handler.eventType());

        var stationEvent = new StationEvent(
                42,
                STATION_ID,
                "Übungsabend",
                "Beschreibung",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.OR,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verify(notificationService)
                .notifyStation(eq(STATION_ID), eq(NotificationType.NEW_EVENT), any(NotificationData.class));
    }

    @Test
    void eventCreatedTruncatesLongDescription() {
        var handler = new EventCreatedHandler(notificationService);
        String longDesc = "A".repeat(100);
        var stationEvent = new StationEvent(
                42,
                STATION_ID,
                "Test",
                longDesc,
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.OR,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verify(notificationService).notifyStation(eq(STATION_ID), eq(NotificationType.NEW_EVENT), argThat(data -> {
            var map = data.paramsAsMap();
            return map.get("eventDescription").length() <= 83; // 80 + "..."
        }));
    }

    @Test
    void eventCreatedHandlesNullDescription() {
        var handler = new EventCreatedHandler(notificationService);
        var stationEvent = new StationEvent(
                42,
                STATION_ID,
                "Test",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.OR,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verify(notificationService)
                .notifyStation(eq(STATION_ID), eq(NotificationType.NEW_EVENT), any(NotificationData.class));
    }

    // -- EventDeletedHandler --

    @Test
    void eventDeletedCleansUpNotifications() {
        var handler = new EventDeletedHandler(notificationService);
        assertEquals(EventDeleted.class, handler.eventType());

        handler.handle(new EventDeleted(STATION_ID, 42, "Übungsabend"));

        verify(notificationService).deleteByTypeContaining(eq(NotificationType.NEW_EVENT), anyString());
    }

    // -- EventRegistrationStatusHandler --

    @Test
    void eventRegistrationStatusNotifiesMemberAndManagers() {
        var handler = new EventRegistrationStatusHandler(notificationService, memberRepository);
        assertEquals(EventRegistrationStatusChanged.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.EVENT_MANAGER))
                .thenReturn(List.of(member(20), member(21)));

        handler.handle(new EventRegistrationStatusChanged(
                STATION_ID, 42, "Übungsabend", MEMBER_ID, RegistrationStatus.ACCEPTED));

        verify(notificationService)
                .notify(eq(MEMBER_ID), eq(NotificationType.EVENT_REGISTRATION_STATUS), any(NotificationData.class));
        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(20, 21)),
                        eq(NotificationType.EVENT_REGISTRATION_STATUS),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    // -- NewsCreatedHandler --

    @Test
    void newsCreatedNotifiesStation() {
        var handler = new NewsCreatedHandler(notificationService);
        assertEquals(NewsCreated.class, handler.eventType());

        handler.handle(new NewsCreated(STATION_ID, 5, "Neue Nachricht"));

        verify(notificationService)
                .notifyStation(eq(STATION_ID), eq(NotificationType.NEW_NEWS), any(NotificationData.class));
    }

    // -- NewsDeletedHandler --

    @Test
    void newsDeletedCleansUpNewsAndCommentNotifications() {
        var handler = new NewsDeletedHandler(notificationService);
        assertEquals(NewsDeleted.class, handler.eventType());

        handler.handle(new NewsDeleted(STATION_ID, 5, "Alte Nachricht"));

        verify(notificationService).deleteByTypeContaining(eq(NotificationType.NEW_NEWS), anyString());
        verify(notificationService).deleteByTypeContaining(eq(NotificationType.NEWS_COMMENT), anyString());
    }

    // -- FormPublishedHandler --

    @Test
    void formPublishedNotifiesStation() {
        var handler = new FormPublishedHandler(notificationService);
        assertEquals(FormPublished.class, handler.eventType());

        handler.handle(new FormPublished(STATION_ID, 7, "Zufriedenheitsumfrage"));

        verify(notificationService)
                .notifyStation(eq(STATION_ID), eq(NotificationType.NEW_FORM), any(NotificationData.class));
    }

    // -- FormDeletedHandler --

    @Test
    void formDeletedCleansUpNotifications() {
        var handler = new FormDeletedHandler(notificationService);
        assertEquals(FormDeleted.class, handler.eventType());

        handler.handle(new FormDeleted(STATION_ID, 7));

        verify(notificationService).deleteByTypeContaining(eq(NotificationType.NEW_FORM), anyString());
    }

    // -- CommentCreatedHandler --

    @Test
    void commentCreatedNotifiesParentAuthorOnReply() {
        var handler = new CommentCreatedHandler(notificationService, memberRepository);
        assertEquals(CommentCreated.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.NEWS_MANAGER))
                .thenReturn(List.of(member(30)));

        handler.handle(new CommentCreated(
                STATION_ID, CommentEntityType.NEWS, 5, "News Title", 100, 99, 20, MEMBER_ID, "Author", "preview"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.NEWS_COMMENT), any(NotificationData.class));
        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30)), eq(NotificationType.NEWS_COMMENT), any(NotificationData.class), eq(MEMBER_ID));
    }

    @Test
    void commentCreatedSkipsParentNotificationWhenSameAuthor() {
        var handler = new CommentCreatedHandler(notificationService, memberRepository);
        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.NEWS_MANAGER))
                .thenReturn(List.of());

        handler.handle(new CommentCreated(
                STATION_ID,
                CommentEntityType.NEWS,
                5,
                "News Title",
                100,
                99,
                MEMBER_ID,
                MEMBER_ID,
                "Author",
                "preview"));

        verify(notificationService, never()).notifyIfAbsent(eq(MEMBER_ID), any(), any());
    }

    @Test
    void commentCreatedSkipsManagerNotifyForEventComments() {
        var handler = new CommentCreatedHandler(notificationService, memberRepository);

        handler.handle(new CommentCreated(
                STATION_ID,
                CommentEntityType.EVENT,
                5,
                "Event Title",
                100,
                null,
                null,
                MEMBER_ID,
                "Author",
                "preview"));

        verify(notificationService, never()).notifyMembersIfAbsent(anyList(), any(), any(), anyInt());
    }

    @Test
    void commentCreatedNoParentAuthorSkipsParentNotification() {
        var handler = new CommentCreatedHandler(notificationService, memberRepository);
        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.NEWS_MANAGER))
                .thenReturn(List.of(member(30)));

        handler.handle(new CommentCreated(
                STATION_ID, CommentEntityType.NEWS, 5, "News Title", 100, null, null, MEMBER_ID, "Author", "preview"));

        verify(notificationService, never()).notifyIfAbsent(anyInt(), any(), any());
        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30)), eq(NotificationType.NEWS_COMMENT), any(NotificationData.class), eq(MEMBER_ID));
    }

    // -- CommentDeletedHandler --

    @Test
    void commentDeletedCleansUpNotifications() {
        var handler = new CommentDeletedHandler(notificationService);
        assertEquals(CommentDeleted.class, handler.eventType());

        handler.handle(new CommentDeleted(STATION_ID, 100, "comment preview"));

        verify(notificationService).deleteByTypeContaining(eq(NotificationType.NEWS_COMMENT), anyString());
    }

    // -- MentionedInCommentHandler --

    @Test
    void mentionedInCommentNotifiesMentionedMember() {
        var handler = new MentionedInCommentHandler(notificationService);
        assertEquals(MentionedInComment.class, handler.eventType());

        handler.handle(new MentionedInComment(STATION_ID, 25, MEMBER_ID, "Author", CommentEntityType.NEWS, 5));

        verify(notificationService)
                .notifyIfAbsent(eq(25), eq(NotificationType.NEWS_COMMENT), any(NotificationData.class));
    }

    @Test
    void mentionedInCommentUsesEventLinkForEventComments() {
        var handler = new MentionedInCommentHandler(notificationService);

        handler.handle(new MentionedInComment(STATION_ID, 25, MEMBER_ID, "Author", CommentEntityType.EVENT, 5));

        verify(notificationService).notifyIfAbsent(eq(25), eq(NotificationType.NEWS_COMMENT), argThat(data -> "events"
                .equals(data.link().route())));
    }

    // -- ExchangeRequestedHandler --

    @Test
    void exchangeRequestedNotifiesInventoryManagers() {
        var handler = new ExchangeRequestedHandler(notificationService, memberRepository);
        assertEquals(ExchangeRequested.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.INVENTORY_MANAGER))
                .thenReturn(List.of(member(30), member(31)));

        handler.handle(new ExchangeRequested(STATION_ID, 1, MEMBER_ID, "Max", "Helm", "Kaputt"));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30, 31)),
                        eq(NotificationType.EXCHANGE_NEW_REQUEST),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    // -- ExchangeStatusChangedHandler --

    @Test
    void exchangeStatusChangedNotifiesMemberAndManagers() {
        var handler = new ExchangeStatusChangedHandler(notificationService, memberRepository);
        assertEquals(ExchangeStatusChanged.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.INVENTORY_MANAGER))
                .thenReturn(List.of(member(30)));

        handler.handle(new ExchangeStatusChanged(STATION_ID, 1, MEMBER_ID, "Max", "Helm", ExchangeStatus.RECEIVED));

        verify(notificationService)
                .notify(eq(MEMBER_ID), eq(NotificationType.EXCHANGE_STATUS_CHANGE), any(NotificationData.class));
        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30)),
                        eq(NotificationType.EXCHANGE_STATUS_CHANGE),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    // -- ProcurementCreatedHandler --

    @Test
    void procurementCreatedNotifiesMember() {
        var handler = new ProcurementCreatedHandler(notificationService);
        assertEquals(ProcurementCreated.class, handler.eventType());

        handler.handle(new ProcurementCreated(STATION_ID, MEMBER_ID, "Schlauch"));

        verify(notificationService)
                .notify(eq(MEMBER_ID), eq(NotificationType.PROCUREMENT_REQUESTED), any(NotificationData.class));
    }

    // -- ProcurementFulfilledHandler --

    @Test
    void procurementFulfilledNotifiesMember() {
        var handler = new ProcurementFulfilledHandler(notificationService);
        assertEquals(ProcurementFulfilled.class, handler.eventType());

        handler.handle(new ProcurementFulfilled(STATION_ID, MEMBER_ID, "Schlauch"));

        verify(notificationService)
                .notify(eq(MEMBER_ID), eq(NotificationType.PROCUREMENT_FULFILLED), any(NotificationData.class));
    }

    // -- MembersAddedToGroupHandler --

    @Test
    void membersAddedToGroupNotifiesAllMembers() {
        var handler = new MembersAddedToGroupHandler(notificationService);
        assertEquals(MembersAddedToGroup.class, handler.eventType());

        var memberIds = List.of(10, 11, 12);
        handler.handle(new MembersAddedToGroup(STATION_ID, "Anfänger", memberIds));

        verify(notificationService)
                .notifyMembers(eq(memberIds), eq(NotificationType.MEMBER_ADDED_TO_GROUP), any(NotificationData.class));
    }

    // -- LendingRequestedHandler --

    @Test
    void lendingRequestedNotifiesOwningStationManagers() {
        var handler = new LendingRequestedHandler(notificationService);
        assertEquals(LendingRequested.class, handler.eventType());

        int owningStationId = 2;
        handler.handle(new LendingRequested(STATION_ID, owningStationId, 99, "Feuerwehr Ost", "2x Schlauch"));

        verify(notificationService)
                .notifyMembersWithRole(
                        eq(owningStationId),
                        eq("INVENTORY_MANAGER"),
                        eq(NotificationType.LENDING_NEW_REQUEST),
                        any(NotificationData.class));
    }

    // -- LendingStatusChangedHandler --

    @Test
    void lendingStatusChangedNotifiesTargetStation() {
        var handler = new LendingStatusChangedHandler(notificationService);
        assertEquals(LendingStatusChanged.class, handler.eventType());

        int targetStationId = 3;
        handler.handle(new LendingStatusChanged(
                STATION_ID,
                targetStationId,
                99,
                NotificationType.LENDING_STATUS_CHANGE,
                "Feuerwehr West",
                LendingStatus.APPROVED));

        verify(notificationService)
                .notifyMembersWithRole(
                        eq(targetStationId),
                        eq("INVENTORY_MANAGER"),
                        eq(NotificationType.LENDING_STATUS_CHANGE),
                        any(NotificationData.class));
    }

    // -- LendingMessageSentHandler --

    @Test
    void lendingMessageSentNotifiesTargetStation() {
        var handler = new LendingMessageSentHandler(notificationService);
        assertEquals(LendingMessageSent.class, handler.eventType());

        int targetStationId = 3;
        handler.handle(new LendingMessageSent(STATION_ID, targetStationId, 99, "Feuerwehr West", "Max Mustermann"));

        verify(notificationService)
                .notifyMembersWithRole(
                        eq(targetStationId),
                        eq("INVENTORY_MANAGER"),
                        eq(NotificationType.LENDING_NEW_MESSAGE),
                        any(NotificationData.class));
    }

    // -- RegistrationDeadlineExpiredHandler --

    @Test
    void registrationDeadlineExpiredNotifiesEventManagers() {
        var handler = new RegistrationDeadlineExpiredHandler(notificationService, memberRepository);
        assertEquals(RegistrationDeadlineExpired.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.EVENT_MANAGER))
                .thenReturn(List.of(member(20), member(21)));

        handler.handle(new RegistrationDeadlineExpired(STATION_ID, 42, "Übungsabend", 5));

        verify(notificationService)
                .notifyMembers(
                        eq(List.of(20, 21)),
                        eq(NotificationType.REGISTRATION_DEADLINE_EXPIRED),
                        any(NotificationData.class));
    }

    // -- BoardTicketChangedHandler --

    @Test
    void boardTicketChangedNotifiesWatchers() {
        var handler = new BoardTicketChangedHandler(notificationService);
        assertEquals(BoardTicketChanged.class, handler.eventType());

        handler.handle(new BoardTicketChanged(
                STATION_ID, 1, 42, "Dev Board", "DEV-42", "Neuer Kommentar", MEMBER_ID, List.of(20, 21)));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(20, 21)),
                        eq(NotificationType.BOARD_TICKET_UPDATE),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    @Test
    void boardTicketChangedSkipsEmptyWatchers() {
        var handler = new BoardTicketChangedHandler(notificationService);

        handler.handle(
                new BoardTicketChanged(STATION_ID, 1, 42, "Dev Board", "DEV-42", "Update", MEMBER_ID, List.of()));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of()),
                        eq(NotificationType.BOARD_TICKET_UPDATE),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }
}
