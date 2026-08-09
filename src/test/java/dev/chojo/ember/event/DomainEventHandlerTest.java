/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.events.BoardTicketChanged;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.event.events.EventsBatchCreated;
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
import dev.chojo.ember.event.events.StorageWarningEvent;
import dev.chojo.ember.event.events.WaitlistPublicRegistration;
import dev.chojo.ember.event.handlers.BoardTicketChangedHandler;
import dev.chojo.ember.event.handlers.BulkMentionedInCommentHandler;
import dev.chojo.ember.event.handlers.CommentCreatedHandler;
import dev.chojo.ember.event.handlers.CommentDeletedHandler;
import dev.chojo.ember.event.handlers.EventCreatedHandler;
import dev.chojo.ember.event.handlers.EventDeletedHandler;
import dev.chojo.ember.event.handlers.EventRegistrationStatusHandler;
import dev.chojo.ember.event.handlers.EventsBatchCreatedHandler;
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
import dev.chojo.ember.event.handlers.StorageWarningHandler;
import dev.chojo.ember.event.handlers.WaitlistPublicRegistrationHandler;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomainEventHandlerTest {
    private NotificationService notificationService;
    private StationMemberRepository memberRepository;
    private MemberGroupRepository memberGroupRepository;
    private EventRepository eventRepository;
    private EventRegistrationRepository registrationRepository;
    private RestrictionService restrictionService;

    private static final int STATION_ID = 1;
    private static final int MEMBER_ID = 10;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        memberRepository = mock(StationMemberRepository.class);
        memberGroupRepository = mock(MemberGroupRepository.class);
        eventRepository = mock(EventRepository.class);
        registrationRepository = mock(EventRegistrationRepository.class);
        restrictionService = mock(RestrictionService.class);
    }

    private StationMember member(int id) {
        return new StationMember(
                id, STATION_ID, UUID.randomUUID(), id, false, null, "Member " + id, StationUserType.MEMBER, null);
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
                false,
                null);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verify(notificationService)
                .notifyStation(eq(STATION_ID), eq(NotificationType.NEW_EVENT), any(NotificationData.class));
    }

    @Test
    void eventCreatedPassesFullDescriptionThrough() {
        // Handler now passes the full description through; word-boundary truncation lives in
        // the feed renderer (NotificationService.truncateSnippet) so we don't mangle text
        // mid-word at the publisher.
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
                false,
                null);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verify(notificationService).notifyStation(eq(STATION_ID), eq(NotificationType.NEW_EVENT), argThat(data -> {
            var map = data.paramsAsMap();
            // Full 100 chars survive — no handler-side truncation any more.
            return map.get("eventDescription").length() == 100;
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
                false,
                null);
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

        verify(notificationService).deleteByTypeContaining(eq(NotificationType.NEW_EVENT), any(NotificationData.class));
    }

    // -- EventsBatchCreatedHandler --

    @Test
    void eventsBatchCreatedEmitsSingleAggregateNotification() {
        var handler = new EventsBatchCreatedHandler(notificationService);
        assertEquals(EventsBatchCreated.class, handler.eventType());

        var events = List.of(
                stationEvent(1, "Probe 1"),
                stationEvent(2, "Probe 2"),
                stationEvent(3, "Probe 3"),
                stationEvent(4, "Probe 4"));
        handler.handle(new EventsBatchCreated(STATION_ID, events));

        verify(notificationService)
                .notifyStation(eq(STATION_ID), eq(NotificationType.NEW_EVENTS_BATCH), argThat(data -> {
                    var map = data.paramsAsMap();
                    // preview shows the first 3 event names plus an ellipsis marker for the rest
                    String preview = map.get("eventPreview");
                    return "4".equals(map.get("count"))
                            && preview != null
                            && preview.contains("Probe 1")
                            && preview.contains("Probe 2")
                            && preview.contains("Probe 3")
                            && preview.contains("…")
                            && !preview.contains("Probe 4");
                }));
    }

    @Test
    void eventsBatchCreatedDoesNothingForEmptyBatch() {
        var handler = new EventsBatchCreatedHandler(notificationService);

        handler.handle(new EventsBatchCreated(STATION_ID, List.of()));

        verify(notificationService, never())
                .notifyStation(anyInt(), eq(NotificationType.NEW_EVENTS_BATCH), any(NotificationData.class));
    }

    private StationEvent stationEvent(int id, String name) {
        return new StationEvent(
                id,
                STATION_ID,
                name,
                "",
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
                false,
                null);
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

        handler.handle(new NewsCreated(STATION_ID, 5, "Neue Nachricht", "Test Author", "Vorschau-Text"));

        verify(notificationService)
                .notifyStation(eq(STATION_ID), eq(NotificationType.NEW_NEWS), any(NotificationData.class));
    }

    // -- NewsDeletedHandler --

    @Test
    void newsDeletedCleansUpNewsAndCommentNotifications() {
        var handler = new NewsDeletedHandler(notificationService);
        assertEquals(NewsDeleted.class, handler.eventType());

        handler.handle(new NewsDeleted(STATION_ID, 5, "Alte Nachricht"));

        verify(notificationService).deleteByTypeContaining(eq(NotificationType.NEW_NEWS), any(NotificationData.class));
        verify(notificationService)
                .deleteByTypeContaining(eq(NotificationType.NEWS_COMMENT), any(NotificationData.class));
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

        verify(notificationService).deleteByTypeContaining(eq(NotificationType.NEW_FORM), any(NotificationData.class));
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

        verify(notificationService)
                .deleteByTypeContaining(eq(NotificationType.NEWS_COMMENT), any(NotificationData.class));
    }

    // -- MentionedInCommentHandler --

    @Test
    void mentionedInCommentNotifiesMentionedMember() {
        var handler = new MentionedInCommentHandler(notificationService);
        assertEquals(MentionedInComment.class, handler.eventType());

        handler.handle(new MentionedInComment(
                STATION_ID, 25, MEMBER_ID, "Author", CommentEntityType.NEWS, 5, "Test Title", "hi there"));

        verify(notificationService)
                .notifyIfAbsent(eq(25), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void mentionedInCommentUsesEventLinkForEventComments() {
        var handler = new MentionedInCommentHandler(notificationService);

        handler.handle(new MentionedInComment(
                STATION_ID, 25, MEMBER_ID, "Author", CommentEntityType.EVENT, 5, "Test Title", "hi there"));

        verify(notificationService)
                .notifyIfAbsent(eq(25), eq(NotificationType.COMMENT_MENTION), argThat(data -> "event-detail"
                        .equals(data.link().route())));
    }

    // -- BulkMentionedInCommentHandler --

    private BulkMentionedInCommentHandler bulkHandler() {
        return new BulkMentionedInCommentHandler(
                notificationService,
                memberGroupRepository,
                eventRepository,
                registrationRepository,
                memberRepository,
                restrictionService);
    }

    @Test
    void bulkMentionedEventType() {
        assertEquals(BulkMentionedInComment.class, bulkHandler().eventType());
    }

    @Test
    void bulkMentionGroupNotifiesGroupMembers() {
        when(memberGroupRepository.findMembers(5)).thenReturn(List.of(member(20), member(21)));
        when(memberRepository.findManagers(20)).thenReturn(List.of());
        when(memberRepository.findManagers(21)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        MEMBER_ID,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.GROUP,
                        5,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService)
                .notifyIfAbsent(eq(21), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionGroupSkipsAuthor() {
        when(memberGroupRepository.findMembers(5)).thenReturn(List.of(member(MEMBER_ID), member(21)));

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        MEMBER_ID,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.GROUP,
                        5,
                        "preview snippet"));

        verify(notificationService, never())
                .notifyIfAbsent(eq(MEMBER_ID), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService)
                .notifyIfAbsent(eq(21), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionEventWithRegistrationRequired() {
        var stationEvent = mock(StationEvent.class);
        when(stationEvent.requiresRegistration()).thenReturn(true);
        when(stationEvent.id()).thenReturn(42);
        when(stationEvent.stationId()).thenReturn(STATION_ID);
        when(eventRepository.findById(42)).thenReturn(Optional.of(stationEvent));
        when(registrationRepository.findByEvent(42))
                .thenReturn(List.of(
                        new EventRegistration(
                                1, 42, 20, LocalDate.now(), RegistrationStatus.ACCEPTED, Instant.now(), null),
                        new EventRegistration(
                                2, 42, 21, LocalDate.now(), RegistrationStatus.DECLINED, Instant.now(), null)));
        when(memberRepository.findManagers(20)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.EVENT,
                        42,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService, never())
                .notifyIfAbsent(eq(21), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionEventWithoutRegistrationAndNoRestrictions() {
        var stationEvent = mock(StationEvent.class);
        when(stationEvent.requiresRegistration()).thenReturn(false);
        when(stationEvent.id()).thenReturn(42);
        when(stationEvent.stationId()).thenReturn(STATION_ID);
        when(eventRepository.findById(42)).thenReturn(Optional.of(stationEvent));
        when(registrationRepository.findByEvent(42)).thenReturn(List.of());
        when(restrictionService.findMembersPassingRestriction(RestrictionType.EVENT, 42, STATION_ID))
                .thenReturn(Set.of());
        when(memberRepository.findByStation(STATION_ID, false)).thenReturn(List.of(member(30), member(31)));
        when(memberRepository.findManagers(30)).thenReturn(List.of());
        when(memberRepository.findManagers(31)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.EVENT,
                        42,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(30), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService)
                .notifyIfAbsent(eq(31), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionEventWithRestrictions() {
        var stationEvent = mock(StationEvent.class);
        when(stationEvent.requiresRegistration()).thenReturn(false);
        when(stationEvent.id()).thenReturn(42);
        when(stationEvent.stationId()).thenReturn(STATION_ID);
        when(eventRepository.findById(42)).thenReturn(Optional.of(stationEvent));
        when(registrationRepository.findByEvent(42)).thenReturn(List.of());
        when(restrictionService.findMembersPassingRestriction(RestrictionType.EVENT, 42, STATION_ID))
                .thenReturn(Set.of(30));
        when(memberRepository.findManagers(30)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.EVENT,
                        42,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(30), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionRegisteredNotifiesAcceptedMembers() {
        when(registrationRepository.findByEvent(42))
                .thenReturn(List.of(
                        new EventRegistration(
                                1, 42, 20, LocalDate.now(), RegistrationStatus.ACCEPTED, Instant.now(), null),
                        new EventRegistration(
                                2, 42, 21, LocalDate.now(), RegistrationStatus.DECLINED, Instant.now(), null)));
        when(memberRepository.findManagers(20)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.REGISTERED,
                        42,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService, never())
                .notifyIfAbsent(eq(21), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionDeclinedNotifiesDeclinedMembers() {
        when(registrationRepository.findByEvent(42))
                .thenReturn(List.of(
                        new EventRegistration(
                                1, 42, 20, LocalDate.now(), RegistrationStatus.ACCEPTED, Instant.now(), null),
                        new EventRegistration(
                                2, 42, 21, LocalDate.now(), RegistrationStatus.DECLINED, Instant.now(), null)));
        when(memberRepository.findManagers(21)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.DECLINED,
                        42,
                        "preview snippet"));

        verify(notificationService, never())
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService)
                .notifyIfAbsent(eq(21), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionAddsGuardiansForNonGroupMentions() {
        when(registrationRepository.findByEvent(42))
                .thenReturn(List.of(new EventRegistration(
                        1, 42, 20, LocalDate.now(), RegistrationStatus.ACCEPTED, Instant.now(), null)));
        when(memberRepository.findManagers(20)).thenReturn(List.of(member(50)));
        when(memberRepository.findManagers(50)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.REGISTERED,
                        42,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService)
                .notifyIfAbsent(eq(50), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionUsesCorrectLinkForEntityType() {
        when(memberGroupRepository.findMembers(5)).thenReturn(List.of(member(20)));

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.EVENT,
                        1,
                        "Title",
                        MentionType.GROUP,
                        5,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), argThat(data -> "event-detail"
                        .equals(data.link().route())));
    }

    // -- ExchangeRequestedHandler --

    @Test
    void exchangeRequestedNotifiesInventoryManagers() {
        var handler = new ExchangeRequestedHandler(notificationService, memberRepository);
        assertEquals(ExchangeRequested.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.INVENTORY_MANAGER))
                .thenReturn(List.of(member(30), member(31)));

        handler.handle(new ExchangeRequested(STATION_ID, 1, MEMBER_ID, "Max", 99, "Helm", "Kaputt"));

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

        handler.handle(new ExchangeStatusChanged(STATION_ID, 1, MEMBER_ID, "Max", 99, "Helm", ExchangeStatus.RECEIVED));

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

        handler.handle(new ProcurementCreated(STATION_ID, MEMBER_ID, 99, "Schlauch"));

        verify(notificationService)
                .notify(eq(MEMBER_ID), eq(NotificationType.PROCUREMENT_REQUESTED), any(NotificationData.class));
    }

    // -- ProcurementFulfilledHandler --

    @Test
    void procurementFulfilledNotifiesMember() {
        var handler = new ProcurementFulfilledHandler(notificationService);
        assertEquals(ProcurementFulfilled.class, handler.eventType());

        handler.handle(new ProcurementFulfilled(STATION_ID, MEMBER_ID, 99, "Schlauch"));

        verify(notificationService)
                .notify(eq(MEMBER_ID), eq(NotificationType.PROCUREMENT_FULFILLED), any(NotificationData.class));
    }

    // -- MembersAddedToGroupHandler --

    @Test
    void membersAddedToGroupNotifiesAllMembers() {
        var handler = new MembersAddedToGroupHandler(notificationService, memberRepository);
        assertEquals(MembersAddedToGroup.class, handler.eventType());

        var memberIds = List.of(10, 11, 12);
        handler.handle(new MembersAddedToGroup(STATION_ID, "Anfänger", memberIds, null));

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
                STATION_ID, 1, 42, "DEV", 7, "Dev Board", "DEV-7", "Neuer Kommentar", MEMBER_ID, List.of(20, 21)));

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

        handler.handle(new BoardTicketChanged(
                STATION_ID, 1, 42, "DEV", 7, "Dev Board", "DEV-7", "Update", MEMBER_ID, List.of()));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of()),
                        eq(NotificationType.BOARD_TICKET_UPDATE),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    @Test
    void storageWarningNotifiesStationManagers() {
        var handler = new StorageWarningHandler(notificationService);
        var event = new StorageWarningEvent(STATION_ID, 85, 4_500_000_000L, 5_368_709_120L);

        assertEquals(StorageWarningEvent.class, handler.eventType());

        handler.handle(event);

        verify(notificationService)
                .notifyMembersWithRole(
                        eq(STATION_ID),
                        eq("STATION_MANAGER"),
                        eq(NotificationType.STORAGE_WARNING),
                        any(NotificationData.class));
    }

    @Test
    void waitlistPublicRegistrationHandlerNotifiesWaitlistEditRole() {
        var handler = new WaitlistPublicRegistrationHandler(notificationService);
        var event = new WaitlistPublicRegistration(STATION_ID, "Max Müller", "Warteliste 2026");

        assertEquals(WaitlistPublicRegistration.class, handler.eventType());

        handler.handle(event);

        verify(notificationService)
                .notifyMembersWithRole(
                        eq(STATION_ID),
                        eq("WAITLIST_EDIT"),
                        eq(NotificationType.WAITLIST_PUBLIC_REGISTRATION),
                        any(NotificationData.class));
    }
}
