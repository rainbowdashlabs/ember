/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.events.BoardTicketChanged;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.event.events.ClusterItemIssued;
import dev.chojo.ember.event.events.ClusterItemLost;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.event.events.EventsBatchCreated;
import dev.chojo.ember.event.events.FormDeleted;
import dev.chojo.ember.event.events.FormPublished;
import dev.chojo.ember.event.events.LendingMessageSent;
import dev.chojo.ember.event.events.LendingRequested;
import dev.chojo.ember.event.events.LendingStatusChanged;
import dev.chojo.ember.event.events.MembersAddedToGroup;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.event.events.MovementAdvanced;
import dev.chojo.ember.event.events.MovementDeclined;
import dev.chojo.ember.event.events.MovementStarted;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.event.events.NewsDeleted;
import dev.chojo.ember.event.events.ProcurementCreated;
import dev.chojo.ember.event.events.ProcurementFulfilled;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.event.events.StorageWarningEvent;
import dev.chojo.ember.event.events.WaitlistPublicRegistration;
import dev.chojo.ember.event.handlers.BoardTicketChangedHandler;
import dev.chojo.ember.event.handlers.BulkMentionedInCommentHandler;
import dev.chojo.ember.event.handlers.ClusterItemIssuedHandler;
import dev.chojo.ember.event.handlers.ClusterItemLostHandler;
import dev.chojo.ember.event.handlers.CommentCreatedHandler;
import dev.chojo.ember.event.handlers.CommentDeletedHandler;
import dev.chojo.ember.event.handlers.EventCreatedHandler;
import dev.chojo.ember.event.handlers.EventDeletedHandler;
import dev.chojo.ember.event.handlers.EventRegistrationStatusHandler;
import dev.chojo.ember.event.handlers.EventsBatchCreatedHandler;
import dev.chojo.ember.event.handlers.FormDeletedHandler;
import dev.chojo.ember.event.handlers.FormPublishedHandler;
import dev.chojo.ember.event.handlers.LendingMessageSentHandler;
import dev.chojo.ember.event.handlers.LendingRequestedHandler;
import dev.chojo.ember.event.handlers.LendingStatusChangedHandler;
import dev.chojo.ember.event.handlers.MembersAddedToGroupHandler;
import dev.chojo.ember.event.handlers.MentionedInCommentHandler;
import dev.chojo.ember.event.handlers.MovementAdvancedHandler;
import dev.chojo.ember.event.handlers.MovementDeclinedHandler;
import dev.chojo.ember.event.handlers.MovementStartedHandler;
import dev.chojo.ember.event.handlers.NewsCreatedHandler;
import dev.chojo.ember.event.handlers.NewsDeletedHandler;
import dev.chojo.ember.event.handlers.ProcurementCreatedHandler;
import dev.chojo.ember.event.handlers.ProcurementFulfilledHandler;
import dev.chojo.ember.event.handlers.RegistrationDeadlineExpiredHandler;
import dev.chojo.ember.event.handlers.StorageWarningHandler;
import dev.chojo.ember.event.handlers.WaitlistPublicRegistrationHandler;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
    private ClusterService clusterService;
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
        clusterService = mock(ClusterService.class);
        memberGroupRepository = mock(MemberGroupRepository.class);
        eventRepository = mock(EventRepository.class);
        registrationRepository = mock(EventRegistrationRepository.class);
        restrictionService = mock(RestrictionService.class);
    }

    private StationMember member(int id) {
        return new StationMember(
                id, STATION_ID, UUID.randomUUID(), id, false, null, "Member " + id, StationUserType.MEMBER, null);
    }

    /** A mentioned group that belongs to the station the comment was written in. */
    private void groupInStation(int groupId) {
        when(memberGroupRepository.findById(groupId))
                .thenReturn(Optional.of(new MemberGroup(groupId, STATION_ID, "Crew", null, 0)));
    }

    /** A mentioned event that belongs to the station the comment was written in. */
    private void eventInStation(int eventId) {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(stationEvent(eventId, "Event " + eventId)));
    }

    // -- EventCreatedHandler --

    @Test
    void eventCreatedNotifiesStation() {
        var handler = new EventCreatedHandler(notificationService, restrictionService);
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
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
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
        var handler = new EventCreatedHandler(notificationService, restrictionService);
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
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verify(notificationService).notifyStation(eq(STATION_ID), eq(NotificationType.NEW_EVENT), argThat(data -> {
            var map = data.paramsAsMap();
            // Full 100 chars survive - no handler-side truncation any more.
            return map.get("eventDescription").length() == 100;
        }));
    }

    @Test
    void eventCreatedHandlesNullDescription() {
        var handler = new EventCreatedHandler(notificationService, restrictionService);
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
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
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

        verify(notificationService).deleteAllPointingAt(NotificationLinks.event(42));
        verify(notificationService).deleteAllPointingAt(NotificationLinks.eventDates(42));
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
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
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

        verify(notificationService).deleteAllPointingAt(NotificationLinks.news(5));
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

        verify(notificationService).deleteAllPointingAt(NotificationLinks.form(7));
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
                .notifyIfAbsent(eq(20), eq(NotificationType.NEWS_COMMENT), argThat(data -> NotificationLinks.comment(
                                CommentEntityType.NEWS, 5, 100)
                        .equals(data.link())));
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
    void commentDeletedWithdrawsWhatNamesThatComment() {
        var handler = new CommentDeletedHandler(notificationService);
        assertEquals(CommentDeleted.class, handler.eventType());

        handler.handle(new CommentDeleted(STATION_ID, CommentEntityType.NEWS, 100));

        verify(notificationService).deleteAllPointingAt(NotificationLinks.commentAlone(CommentEntityType.NEWS, 100));
    }

    // -- MentionedInCommentHandler --

    @Test
    void mentionedInCommentNotifiesMentionedMember() {
        var handler = new MentionedInCommentHandler(notificationService);
        assertEquals(MentionedInComment.class, handler.eventType());

        handler.handle(new MentionedInComment(
                STATION_ID, 25, MEMBER_ID, "Author", CommentEntityType.NEWS, 5, "Test Title", 70, "hi there"));

        verify(notificationService)
                .notifyIfAbsent(eq(25), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void mentionedInCommentUsesEventLinkForEventComments() {
        var handler = new MentionedInCommentHandler(notificationService);

        handler.handle(new MentionedInComment(
                STATION_ID, 25, MEMBER_ID, "Author", CommentEntityType.EVENT, 5, "Test Title", 70, "hi there"));

        verify(notificationService)
                .notifyIfAbsent(eq(25), eq(NotificationType.COMMENT_MENTION), argThat(data -> NotificationLinks.comment(
                                CommentEntityType.EVENT, 5, 70)
                        .equals(data.link())));
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
        groupInStation(5);
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
                        70,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService)
                .notifyIfAbsent(eq(21), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionGroupSkipsAuthor() {
        groupInStation(5);
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
                        70,
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
                        70,
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
        when(restrictionService.findMembersPassingRestriction(RestrictionType.EVENT_VIEW, 42, STATION_ID))
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
                        70,
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
        when(restrictionService.findMembersPassingRestriction(RestrictionType.EVENT_VIEW, 42, STATION_ID))
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
                        70,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(30), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    /**
     * A mention names a group by an id that runs across the whole instance. Without the station
     * test, a member of one station addresses another station's group and everyone in it is handed
     * the comment.
     */
    @Test
    void bulkMentionOfAnotherStationsGroupNotifiesNobody() {
        when(memberGroupRepository.findById(5))
                .thenReturn(Optional.of(new MemberGroup(5, STATION_ID + 1, "Foreign crew", null, 0)));

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
                        70,
                        "preview snippet"));

        verify(memberGroupRepository, never()).findMembers(anyInt());
        verify(notificationService, never()).notifyIfAbsent(anyInt(), any(), any());
    }

    /** The same for an event: the registrations of another station's event reach nobody. */
    @Test
    void bulkMentionOfAnotherStationsEventNotifiesNobody() {
        var foreignEvent = mock(StationEvent.class);
        when(foreignEvent.stationId()).thenReturn(STATION_ID + 1);
        when(eventRepository.findById(42)).thenReturn(Optional.of(foreignEvent));

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        MEMBER_ID,
                        "Author",
                        CommentEntityType.NEWS,
                        1,
                        "Title",
                        MentionType.REGISTERED,
                        42,
                        70,
                        "preview snippet"));

        verify(registrationRepository, never()).findByEvent(anyInt());
        verify(notificationService, never()).notifyIfAbsent(anyInt(), any(), any());
    }

    @Test
    void bulkMentionRegisteredNotifiesAcceptedMembers() {
        eventInStation(42);
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
                        70,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService, never())
                .notifyIfAbsent(eq(21), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionDeclinedNotifiesDeclinedMembers() {
        eventInStation(42);
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
                        70,
                        "preview snippet"));

        verify(notificationService, never())
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService)
                .notifyIfAbsent(eq(21), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionAddsGuardiansForNonGroupMentions() {
        eventInStation(42);
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
                        70,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
        verify(notificationService)
                .notifyIfAbsent(eq(50), eq(NotificationType.COMMENT_MENTION), any(NotificationData.class));
    }

    @Test
    void bulkMentionUsesCorrectLinkForEntityType() {
        groupInStation(5);
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
                        70,
                        "preview snippet"));

        verify(notificationService)
                .notifyIfAbsent(eq(20), eq(NotificationType.COMMENT_MENTION), argThat(data -> "event-detail"
                        .equals(data.link().route())));
    }

    // -- MovementStartedHandler --

    @Test
    void movementStartedTellsWhoeverTheChainWaitsOn() {
        var handler = new MovementStartedHandler(notificationService, memberRepository, () -> clusterService);
        assertEquals(MovementStarted.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.INVENTORY_MANAGER))
                .thenReturn(List.of(member(30), member(31)));

        handler.handle(new MovementStarted(
                STATION_ID, 1, MEMBER_ID, "Max", 99, "Helm", "Kaputt", MEMBER_ID, StepActor.STATION, null));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30, 31)),
                        eq(NotificationType.EXCHANGE_NEW_REQUEST),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    // -- MovementAdvancedHandler --

    @Test
    void movementAdvancedTellsTheStationWhenItsStepIsNext() {
        var handler = new MovementAdvancedHandler(notificationService, memberRepository, () -> clusterService);
        assertEquals(MovementAdvanced.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.INVENTORY_MANAGER))
                .thenReturn(List.of(member(30)));

        handler.handle(new MovementAdvanced(
                STATION_ID, 1, MEMBER_ID, 99, "Helm", "Tausch angekündigt", MEMBER_ID, StepActor.STATION, null));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30)),
                        eq(NotificationType.EXCHANGE_STATUS_CHANGE),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    @Test
    void movementAdvancedTellsTheMemberWhenTheirStepIsNext() {
        var handler = new MovementAdvancedHandler(notificationService, memberRepository, () -> clusterService);

        handler.handle(new MovementAdvanced(
                STATION_ID, 1, MEMBER_ID, 99, "Helm", "Ersatz erhalten", 30, StepActor.MEMBER, null));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(MEMBER_ID)),
                        eq(NotificationType.EXCHANGE_STATUS_CHANGE),
                        any(NotificationData.class),
                        eq(30));
        verify(memberRepository, never()).findMembersWithPermission(anyInt(), any());
    }

    @Test
    void aChainThatHasEndedTellsTheMemberItConcerned() {
        var handler = new MovementAdvancedHandler(notificationService, memberRepository, () -> clusterService);

        handler.handle(new MovementAdvanced(STATION_ID, 1, MEMBER_ID, 99, "Helm", "Ersatz ausgegeben", 30, null, null));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(MEMBER_ID)),
                        eq(NotificationType.EXCHANGE_STATUS_CHANGE),
                        any(NotificationData.class),
                        eq(30));
    }

    /**
     * An owner that does not run here cannot be told anything, so the station that stands in for it
     * is who hears. Nothing is addressed to a party that could never receive it.
     */
    @Test
    void anOwnerStepIsAnnouncedToTheStationStandingInForIt() {
        var handler = new MovementAdvancedHandler(notificationService, memberRepository, () -> clusterService);

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.INVENTORY_MANAGER))
                .thenReturn(List.of(member(30)));

        handler.handle(new MovementAdvanced(
                STATION_ID, 1, MEMBER_ID, 99, "Helm", "An den Träger geschickt", MEMBER_ID, StepActor.OWNER, null));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30)),
                        eq(NotificationType.EXCHANGE_STATUS_CHANGE),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    /**
     * An owner that is a cluster on this instance can be told, and is the only party that can answer
     * the step. The station hears nothing: it has nothing to do but wait.
     */
    @Test
    void anOwnerStepOnClusterGearIsAnnouncedToTheCluster() {
        var handler = new MovementAdvancedHandler(notificationService, memberRepository, () -> clusterService);

        when(clusterService.findMemberIdsWith(7, ClusterPermission.CLUSTER_INVENTORY_MANAGER))
                .thenReturn(List.of(40, 41));

        handler.handle(new MovementAdvanced(
                STATION_ID, 1, MEMBER_ID, 99, "Helm", "An den Träger geschickt", MEMBER_ID, StepActor.OWNER, 7));

        verify(notificationService)
                .notifyClusterMembersIfAbsent(
                        eq(List.of(40, 41)),
                        eq(NotificationType.EXCHANGE_STATUS_CHANGE),
                        argThat(data -> "cluster-movements".equals(data.link().route())),
                        isNull());
        verify(notificationService).notifyMembersIfAbsent(eq(List.of()), any(), any(NotificationData.class), anyInt());
        verify(memberRepository, never()).findMembersWithPermission(anyInt(), any());
    }

    /**
     * A chain that ends with nobody at either end tells nobody. It is the one case where the right
     * answer is silence rather than a fallback recipient.
     */
    @Test
    void aChainThatEndsWithNoMemberTellsNobody() {
        var handler = new MovementAdvancedHandler(notificationService, memberRepository, () -> clusterService);

        handler.handle(new MovementAdvanced(STATION_ID, 1, null, 99, "Helm", "Eingelagert", 30, null, null));

        verify(notificationService).notifyMembersIfAbsent(eq(List.of()), any(), any(NotificationData.class), anyInt());
        verify(notificationService)
                .notifyClusterMembersIfAbsent(eq(List.of()), any(), any(NotificationData.class), isNull());
    }

    /** A station with a name, which is the only thing the lost-gear message reads off it. */
    private static Station station(String name) {
        return new Station(
                STATION_ID,
                UUID.randomUUID(),
                name,
                "Europe/Berlin",
                "de-DE",
                null,
                "ember",
                true,
                null,
                ThemeFeel.ROUNDED,
                true,
                PublicKbMode.OFF,
                null,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false);
    }

    // -- ClusterItemIssuedHandler --

    /**
     * Being told that something is coming is a different sentence from being asked to acknowledge a step,
     * so it reaches the station as its own message and lands on the movement carrying it.
     */
    @Test
    void clusterItemIssuedTellsTheStationWhatIsOnItsWay() {
        var handler = new ClusterItemIssuedHandler(notificationService, memberRepository);
        assertEquals(ClusterItemIssued.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.INVENTORY_MANAGER))
                .thenReturn(List.of(member(30), member(31)));

        handler.handle(new ClusterItemIssued(STATION_ID, 5, "Kreisverband Musterstadt", "Jacke"));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30, 31)),
                        eq(NotificationType.CLUSTER_ITEM_ISSUED),
                        argThat(data ->
                                "inventory-movement-detail".equals(data.link().route())),
                        eq(0));
    }

    // -- ClusterItemLostHandler --

    @Test
    void clusterItemLostTellsTheClusterAndNamesTheStation() {
        var stationRepository = mock(StationRepository.class);
        var handler = new ClusterItemLostHandler(notificationService, stationRepository, () -> clusterService);
        assertEquals(ClusterItemLost.class, handler.eventType());

        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(station("JF Nachbarstadt")));
        when(clusterService.findMemberIdsWith(7, ClusterPermission.CLUSTER_INVENTORY_MANAGER))
                .thenReturn(List.of(40));

        handler.handle(new ClusterItemLost(7, "Helm", STATION_ID));

        verify(notificationService)
                .notifyClusterMembersIfAbsent(
                        eq(List.of(40)),
                        eq(NotificationType.CLUSTER_ITEM_LOST),
                        argThat(data -> "cluster-inventory".equals(data.link().route())),
                        isNull());
    }

    /** A station that has gone since the report still leaves a message worth reading. */
    @Test
    void clusterItemLostSurvivesAStationThatIsNoLongerThere() {
        var stationRepository = mock(StationRepository.class);
        var handler = new ClusterItemLostHandler(notificationService, stationRepository, () -> clusterService);

        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.empty());
        when(clusterService.findMemberIdsWith(7, ClusterPermission.CLUSTER_INVENTORY_MANAGER))
                .thenReturn(List.of(40));

        handler.handle(new ClusterItemLost(7, "Helm", STATION_ID));

        verify(notificationService)
                .notifyClusterMembersIfAbsent(
                        eq(List.of(40)), eq(NotificationType.CLUSTER_ITEM_LOST), any(NotificationData.class), isNull());
    }

    // -- MovementDeclinedHandler --

    @Test
    void movementDeclinedTellsBothEnds() {
        var handler = new MovementDeclinedHandler(notificationService, memberRepository);
        assertEquals(MovementDeclined.class, handler.eventType());

        when(memberRepository.findMembersWithPermission(STATION_ID, StationPermission.INVENTORY_MANAGER))
                .thenReturn(List.of(member(30)));

        handler.handle(new MovementDeclined(STATION_ID, 1, MEMBER_ID, 99, "Helm", "Kein Ersatz", 30));

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(30, MEMBER_ID)),
                        eq(NotificationType.MOVEMENT_DECLINED),
                        any(NotificationData.class),
                        eq(30));
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
