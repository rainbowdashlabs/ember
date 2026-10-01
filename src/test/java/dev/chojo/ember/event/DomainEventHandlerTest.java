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
import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.board.handler.BoardTicketChangedHandler;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.comment.handler.BulkMentionedInCommentHandler;
import dev.chojo.ember.feature.comment.handler.CommentCreatedHandler;
import dev.chojo.ember.feature.comment.handler.CommentDeletedHandler;
import dev.chojo.ember.feature.comment.handler.MentionedInCommentHandler;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.handler.EventCreatedHandler;
import dev.chojo.ember.feature.events.handler.EventDeletedHandler;
import dev.chojo.ember.feature.events.handler.EventRegistrationStatusHandler;
import dev.chojo.ember.feature.events.handler.EventsBatchCreatedHandler;
import dev.chojo.ember.feature.events.handler.RegistrationDeadlineExpiredHandler;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.handler.LendingMessageSentHandler;
import dev.chojo.ember.feature.federation.handler.LendingRequestedHandler;
import dev.chojo.ember.feature.federation.handler.LendingStatusChangedHandler;
import dev.chojo.ember.feature.form.handler.FormDeletedHandler;
import dev.chojo.ember.feature.form.handler.FormPublishedHandler;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.handler.ClusterItemIssuedHandler;
import dev.chojo.ember.feature.inventory.handler.ClusterItemLostHandler;
import dev.chojo.ember.feature.inventory.handler.MovementAdvancedHandler;
import dev.chojo.ember.feature.inventory.handler.MovementDeclinedHandler;
import dev.chojo.ember.feature.inventory.handler.MovementStartedHandler;
import dev.chojo.ember.feature.inventory.handler.ProcurementCreatedHandler;
import dev.chojo.ember.feature.inventory.handler.ProcurementFulfilledHandler;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.handler.MembersAddedToGroupHandler;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.news.handler.NewsCreatedHandler;
import dev.chojo.ember.feature.news.handler.NewsDeletedHandler;
import dev.chojo.ember.feature.notifications.entity.Audience;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.handler.StorageWarningHandler;
import dev.chojo.ember.feature.waitinglist.handler.WaitlistPublicRegistrationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomainEventHandlerTest {
    private Notifier notifier;
    private StationMemberRepository memberRepository;
    private ClusterService clusterService;
    private MemberGroupRepository memberGroupRepository;
    private EventRepository eventRepository;
    private EventRegistrationRepository registrationRepository;
    private RestrictionService restrictionService;

    private static final int STATION_ID = 1;
    private static final int MEMBER_ID = 10;
    private static final NotificationLink NEWS_COMMENT_LINK = NotificationLinks.comment(NotificationLinks.news(5), 100);
    private static final StationAudience NEWS_MANAGERS =
            StationAudience.holders(STATION_ID, StationPermission.NEWS_MANAGER);

    @BeforeEach
    void setUp() {
        notifier = mock(Notifier.class);
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

    /** Verifies the notifier was asked once to tell exactly this audience this type. */
    private void verifyTold(Audience audience, NotificationType type, Delivery delivery) {
        verify(notifier).notify(eq(audience), eq(type), any(NotificationData.class), eq(delivery));
    }

    /** A mentioned group that belongs to the station the comment was written in. */
    private void groupInStation(int groupId) {
        when(memberGroupRepository.findById(groupId))
                .thenReturn(Optional.of(new MemberGroup(groupId, STATION_ID, "Crew", null, 0, null, List.of())));
    }

    /** A mentioned event that belongs to the station the comment was written in. */
    private void eventInStation(int eventId) {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(stationEvent(eventId, "Event " + eventId)));
    }

    @Test
    void eventCreatedNotifiesStation() {
        var handler = new EventCreatedHandler(notifier, restrictionService);
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
                null,
                null,
                null);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verifyTold(StationAudience.wholeStation(STATION_ID), NotificationType.NEW_EVENT, Delivery.EVERY_TIME);
    }

    /** The whole description is passed on, because the feed cuts it at a word boundary on the way out. */
    @Test
    void eventCreatedPassesFullDescriptionThrough() {
        var handler = new EventCreatedHandler(notifier, restrictionService);
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
                null,
                null,
                null);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verify(notifier)
                .notify(
                        eq(StationAudience.wholeStation(STATION_ID)),
                        eq(NotificationType.NEW_EVENT),
                        argThat(data ->
                                data.paramsAsMap().get("eventDescription").length() == 100),
                        eq(Delivery.EVERY_TIME));
    }

    @Test
    void eventCreatedHandlesNullDescription() {
        var handler = new EventCreatedHandler(notifier, restrictionService);
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
                null,
                null,
                null);
        handler.handle(new EventCreated(STATION_ID, stationEvent));

        verify(notifier)
                .notify(
                        eq(StationAudience.wholeStation(STATION_ID)),
                        eq(NotificationType.NEW_EVENT),
                        argThat(data -> "".equals(data.paramsAsMap().get("eventDescription"))),
                        eq(Delivery.EVERY_TIME));
    }

    @Test
    void eventCreatedWithARestrictedAudienceTellsOnlyThatAudience() {
        var handler = new EventCreatedHandler(notifier, restrictionService);
        when(restrictionService.findMembersPassingRestriction(RestrictionType.EVENT_VIEW, 7, STATION_ID))
                .thenReturn(Optional.of(Set.of(30)));

        handler.handle(new EventCreated(STATION_ID, stationEvent(7, "Geheim")));

        verifyTold(StationAudience.members(Set.of(30)), NotificationType.NEW_EVENT, Delivery.EVERY_TIME);
    }

    @Test
    void eventDeletedCleansUpNotifications() {
        var handler = new EventDeletedHandler(notifier);
        assertEquals(EventDeleted.class, handler.eventType());

        handler.handle(new EventDeleted(STATION_ID, 42, "Übungsabend"));

        verify(notifier).withdrawAll(NotificationLinks.event(42));
        verify(notifier).withdrawAll(NotificationLinks.eventDates(42));
    }

    @Test
    void eventsBatchCreatedEmitsSingleAggregateNotification() {
        var handler = batchHandler();
        assertEquals(EventsBatchCreated.class, handler.eventType());

        var events = List.of(
                stationEvent(1, "Probe 1"),
                stationEvent(2, "Probe 2"),
                stationEvent(3, "Probe 3"),
                stationEvent(4, "Probe 4"));
        handler.handle(new EventsBatchCreated(STATION_ID, events));

        verify(notifier)
                .notify(
                        eq(StationAudience.wholeStation(STATION_ID)),
                        eq(NotificationType.NEW_EVENTS_BATCH),
                        argThat(data -> {
                            var map = data.paramsAsMap();
                            String preview = map.get("eventPreview");
                            return "4".equals(map.get("count"))
                                    && preview != null
                                    && preview.contains("Probe 1")
                                    && preview.contains("Probe 2")
                                    && preview.contains("Probe 3")
                                    && preview.contains("…")
                                    && !preview.contains("Probe 4");
                        }),
                        eq(Delivery.EVERY_TIME));
    }

    @Test
    void eventsBatchCreatedDoesNothingForEmptyBatch() {
        var handler = batchHandler();

        handler.handle(new EventsBatchCreated(STATION_ID, List.of()));

        verifyNoInteractions(notifier);
    }

    @Test
    void eventsBatchCreatedTellsEachMemberOnlyAboutTheAppointmentsTheyMaySee() {
        var handler = batchHandler();
        when(restrictionService.findMembersPassingRestriction(RestrictionType.EVENT_VIEW, 2, STATION_ID))
                .thenReturn(Optional.of(Set.of(30)));
        when(restrictionService.findMembersPassingRestriction(RestrictionType.EVENT_VIEW, 3, STATION_ID))
                .thenReturn(Optional.of(Set.of(30)));
        when(memberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(30), member(31)));

        handler.handle(new EventsBatchCreated(
                STATION_ID, List.of(stationEvent(1, "Open"), stationEvent(2, "Hidden"), stationEvent(3, "Closed"))));

        verify(notifier)
                .notify(
                        eq(StationAudience.members(List.of(30))),
                        eq(NotificationType.NEW_EVENTS_BATCH),
                        argThat(data -> "3".equals(data.paramsAsMap().get("count"))),
                        eq(Delivery.EVERY_TIME));
        verify(notifier)
                .notify(
                        eq(StationAudience.members(List.of(31))),
                        eq(NotificationType.NEW_EVENTS_BATCH),
                        argThat(data -> {
                            var map = data.paramsAsMap();
                            return "1".equals(map.get("count")) && "Open".equals(map.get("eventPreview"));
                        }),
                        eq(Delivery.EVERY_TIME));
        verify(notifier, never()).notify(eq(StationAudience.wholeStation(STATION_ID)), any(), any(), any());
    }

    @Test
    void eventsBatchCreatedSkipsMembersWhoMaySeeNoneOfIt() {
        var handler = batchHandler();
        when(restrictionService.findMembersPassingRestriction(RestrictionType.EVENT_VIEW, 1, STATION_ID))
                .thenReturn(Optional.of(Set.of(30)));
        when(memberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(30), member(31)));

        handler.handle(new EventsBatchCreated(STATION_ID, List.of(stationEvent(1, "Hidden"))));

        verifyTold(StationAudience.members(List.of(30)), NotificationType.NEW_EVENTS_BATCH, Delivery.EVERY_TIME);
        verify(notifier, never()).notify(eq(StationAudience.members(List.of(31))), any(), any(), any());
    }

    private EventsBatchCreatedHandler batchHandler() {
        return new EventsBatchCreatedHandler(
                notifier, mock(StationRepository.class), memberRepository, restrictionService);
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
                null,
                null,
                null);
    }

    @Test
    void eventRegistrationStatusNotifiesMemberAndManagers() {
        var handler = new EventRegistrationStatusHandler(notifier);
        assertEquals(EventRegistrationStatusChanged.class, handler.eventType());

        handler.handle(new EventRegistrationStatusChanged(
                STATION_ID, 42, "Übungsabend", MEMBER_ID, "Tim Berger", RegistrationStatus.ACCEPTED));

        verifyTold(StationAudience.member(MEMBER_ID), NotificationType.EVENT_REGISTRATION_STATUS, Delivery.EVERY_TIME);
        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.EVENT_MANAGER)
                        .except(MEMBER_ID),
                NotificationType.EVENT_REGISTRATION_STATUS,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void newsCreatedNotifiesStation() {
        var handler = new NewsCreatedHandler(notifier, restrictionService);
        assertEquals(NewsCreated.class, handler.eventType());
        when(restrictionService.findMembersPassingRestriction(any(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        handler.handle(new NewsCreated(STATION_ID, 5, "Neue Nachricht", "Test Author", "Vorschau-Text"));

        verifyTold(StationAudience.wholeStation(STATION_ID), NotificationType.NEW_NEWS, Delivery.EVERY_TIME);
    }

    @Test
    void newsDeletedCleansUpNewsAndCommentNotifications() {
        var handler = new NewsDeletedHandler(notifier);
        assertEquals(NewsDeleted.class, handler.eventType());

        handler.handle(new NewsDeleted(STATION_ID, 5, "Alte Nachricht"));

        verify(notifier).withdrawAll(NotificationLinks.news(5));
    }

    @Test
    void formPublishedNotifiesStation() {
        var handler = new FormPublishedHandler(notifier, restrictionService);
        assertEquals(FormPublished.class, handler.eventType());
        when(restrictionService.findMembersPassingRestriction(any(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        handler.handle(new FormPublished(STATION_ID, 7, "Zufriedenheitsumfrage"));

        verifyTold(StationAudience.wholeStation(STATION_ID), NotificationType.NEW_FORM, Delivery.EVERY_TIME);
    }

    @Test
    void formDeletedCleansUpNotifications() {
        var handler = new FormDeletedHandler(notifier);
        assertEquals(FormDeleted.class, handler.eventType());

        handler.handle(new FormDeleted(STATION_ID, 7));

        verify(notifier).withdrawAll(NotificationLinks.form(7));
    }

    @Test
    void commentCreatedNotifiesParentAuthorOnReply() {
        var handler = new CommentCreatedHandler(notifier);
        assertEquals(CommentCreated.class, handler.eventType());

        handler.handle(new CommentCreated(
                STATION_ID,
                CommentEntityType.NEWS,
                "News Title",
                NEWS_COMMENT_LINK,
                100,
                99,
                20,
                MEMBER_ID,
                "Author",
                "preview",
                NEWS_MANAGERS));

        verify(notifier)
                .notify(
                        eq(StationAudience.member(20)),
                        eq(NotificationType.NEWS_COMMENT),
                        argThat(data -> NEWS_COMMENT_LINK.equals(data.link())),
                        eq(Delivery.ONCE_WHILE_UNREAD));
        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.NEWS_MANAGER)
                        .except(MEMBER_ID),
                NotificationType.NEWS_COMMENT,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void commentCreatedSkipsParentNotificationWhenSameAuthor() {
        var handler = new CommentCreatedHandler(notifier);

        handler.handle(new CommentCreated(
                STATION_ID,
                CommentEntityType.NEWS,
                "News Title",
                NEWS_COMMENT_LINK,
                100,
                99,
                MEMBER_ID,
                MEMBER_ID,
                "Author",
                "preview",
                NEWS_MANAGERS));

        verify(notifier, never()).notify(eq(StationAudience.member(MEMBER_ID)), any(), any(), any());
    }

    @Test
    void commentCreatedTellsNobodyElseWhenTheTargetNamesNobody() {
        var handler = new CommentCreatedHandler(notifier);

        handler.handle(new CommentCreated(
                STATION_ID,
                CommentEntityType.EVENT,
                "Event Title",
                NotificationLinks.comment(NotificationLinks.event(5), 100),
                100,
                null,
                null,
                MEMBER_ID,
                "Author",
                "preview",
                null));

        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void commentCreatedNoParentAuthorSkipsParentNotification() {
        var handler = new CommentCreatedHandler(notifier);

        handler.handle(new CommentCreated(
                STATION_ID,
                CommentEntityType.NEWS,
                "News Title",
                NEWS_COMMENT_LINK,
                100,
                null,
                null,
                MEMBER_ID,
                "Author",
                "preview",
                NEWS_MANAGERS));

        verify(notifier).notify(any(), any(), any(), any());
        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.NEWS_MANAGER)
                        .except(MEMBER_ID),
                NotificationType.NEWS_COMMENT,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void commentDeletedWithdrawsWhatNamesThatComment() {
        var handler = new CommentDeletedHandler(notifier);
        assertEquals(CommentDeleted.class, handler.eventType());

        handler.handle(new CommentDeleted(STATION_ID, CommentEntityType.NEWS, NEWS_COMMENT_LINK, 100));

        verify(notifier).withdrawAll(new NotificationLink("news-detail", Map.of(), Map.of("comment", 100)));
    }

    @Test
    void mentionedInCommentNotifiesMentionedMember() {
        var handler = new MentionedInCommentHandler(notifier);
        assertEquals(MentionedInComment.class, handler.eventType());

        handler.handle(new MentionedInComment(
                STATION_ID,
                25,
                MEMBER_ID,
                "Author",
                CommentEntityType.NEWS,
                "Test Title",
                NEWS_COMMENT_LINK,
                70,
                "hi there"));

        verifyTold(StationAudience.member(25), NotificationType.COMMENT_MENTION, Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void mentionedInCommentOpensTheLinkItCarries() {
        var handler = new MentionedInCommentHandler(notifier);
        var link = NotificationLinks.comment(NotificationLinks.event(5), 70);

        handler.handle(new MentionedInComment(
                STATION_ID, 25, MEMBER_ID, "Author", CommentEntityType.EVENT, "Test Title", link, 70, "hi there"));

        verify(notifier)
                .notify(
                        eq(StationAudience.member(25)),
                        eq(NotificationType.COMMENT_MENTION),
                        argThat(data -> link.equals(data.link())),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    /**
     * A ticket is reached by its board and its number, not by its id, so the link a mention on one
     * carries has to name all three the way every other ticket notification does. Held against the
     * address the route really asks for rather than against a shape of its own.
     */
    @Test
    void mentionedInATicketCommentNamesTheBoardAndTheNumber() {
        var handler = new MentionedInCommentHandler(notifier);

        handler.handle(new MentionedInComment(
                STATION_ID,
                25,
                MEMBER_ID,
                "DEV-42",
                CommentEntityType.BOARD_TICKET,
                "DEV-42",
                NotificationLinks.comment(NotificationLinks.ticket(new BoardTicketAddress("DEV", 42), 7), 70),
                70,
                "hi there"));

        verify(notifier)
                .notify(
                        eq(StationAudience.member(25)),
                        eq(NotificationType.COMMENT_MENTION),
                        argThat(data -> "ticket-detail".equals(data.link().route())
                                && Map.of("boardKey", "DEV", "ticketNumber", 42, "ticketId", 7)
                                        .equals(data.link().routeParams())
                                && Map.of("comment", 70).equals(data.link().query())),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    private BulkMentionedInCommentHandler bulkHandler() {
        return new BulkMentionedInCommentHandler(
                notifier,
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
                        "Title",
                        MentionType.GROUP,
                        5,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verifyTold(
                StationAudience.members(List.of(20, 21)).except(MEMBER_ID),
                NotificationType.COMMENT_MENTION,
                Delivery.ONCE_WHILE_UNREAD);
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
                        "Title",
                        MentionType.GROUP,
                        5,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verifyTold(
                StationAudience.members(List.of(MEMBER_ID, 21)).except(MEMBER_ID),
                NotificationType.COMMENT_MENTION,
                Delivery.ONCE_WHILE_UNREAD);
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
                        "Title",
                        MentionType.EVENT,
                        42,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verifyTold(
                StationAudience.household(List.of(20)), NotificationType.COMMENT_MENTION, Delivery.ONCE_WHILE_UNREAD);
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
                .thenReturn(Optional.empty());
        when(memberRepository.findByStation(STATION_ID, false)).thenReturn(List.of(member(30), member(31)));
        when(memberRepository.findManagers(30)).thenReturn(List.of());
        when(memberRepository.findManagers(31)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.NEWS,
                        "Title",
                        MentionType.EVENT,
                        42,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verifyTold(
                StationAudience.household(List.of(30, 31)),
                NotificationType.COMMENT_MENTION,
                Delivery.ONCE_WHILE_UNREAD);
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
                .thenReturn(Optional.of(Set.of(30)));
        when(memberRepository.findManagers(30)).thenReturn(List.of());

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        null,
                        "Author",
                        CommentEntityType.NEWS,
                        "Title",
                        MentionType.EVENT,
                        42,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verifyTold(
                StationAudience.household(List.of(30)), NotificationType.COMMENT_MENTION, Delivery.ONCE_WHILE_UNREAD);
    }

    /**
     * A mention names a group by an id that runs across the whole instance. Without the station
     * test, a member of one station addresses another station's group and everyone in it is handed
     * the comment.
     */
    @Test
    void bulkMentionOfAnotherStationsGroupNotifiesNobody() {
        when(memberGroupRepository.findById(5))
                .thenReturn(Optional.of(new MemberGroup(5, STATION_ID + 1, "Foreign crew", null, 0, null, List.of())));

        bulkHandler()
                .handle(new BulkMentionedInComment(
                        STATION_ID,
                        MEMBER_ID,
                        "Author",
                        CommentEntityType.NEWS,
                        "Title",
                        MentionType.GROUP,
                        5,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verify(memberGroupRepository, never()).findMembers(anyInt());
        verifyTold(
                StationAudience.members(List.of()).except(MEMBER_ID),
                NotificationType.COMMENT_MENTION,
                Delivery.ONCE_WHILE_UNREAD);
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
                        "Title",
                        MentionType.REGISTERED,
                        42,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verify(registrationRepository, never()).findByEvent(anyInt());
        verifyTold(
                StationAudience.household(List.of()).except(MEMBER_ID),
                NotificationType.COMMENT_MENTION,
                Delivery.ONCE_WHILE_UNREAD);
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
                        "Title",
                        MentionType.REGISTERED,
                        42,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verifyTold(
                StationAudience.household(List.of(20)), NotificationType.COMMENT_MENTION, Delivery.ONCE_WHILE_UNREAD);
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
                        "Title",
                        MentionType.DECLINED,
                        42,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        verifyTold(
                StationAudience.household(List.of(21)), NotificationType.COMMENT_MENTION, Delivery.ONCE_WHILE_UNREAD);
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
                        "Title",
                        MentionType.REGISTERED,
                        42,
                        NEWS_COMMENT_LINK,
                        70,
                        "preview snippet"));

        var audience = org.mockito.ArgumentCaptor.forClass(Audience.class);
        verify(notifier).notify(audience.capture(), eq(NotificationType.COMMENT_MENTION), any(), any());
        assertEquals(Set.of(20), ((StationAudience) audience.getValue()).wardIds(), "their guardians are told too");
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
                        "Title",
                        MentionType.GROUP,
                        5,
                        NotificationLinks.comment(NotificationLinks.event(1), 70),
                        70,
                        "preview snippet"));

        verify(notifier)
                .notify(
                        eq(StationAudience.members(List.of(20))),
                        eq(NotificationType.COMMENT_MENTION),
                        argThat(data -> "event-detail".equals(data.link().route())),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void movementStartedTellsWhoeverTheChainWaitsOn() {
        var handler = new MovementStartedHandler(notifier);
        assertEquals(MovementStarted.class, handler.eventType());

        handler.handle(new MovementStarted(
                STATION_ID, 1, MEMBER_ID, "Max", 99, "Helm", "Kaputt", MEMBER_ID, StepActor.STATION, null));

        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.INVENTORY_MANAGER)
                        .except(MEMBER_ID),
                NotificationType.MOVEMENT_RAISED,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void movementAdvancedTellsTheStationWhenItsStepIsNext() {
        var handler = new MovementAdvancedHandler(notifier);
        assertEquals(MovementAdvanced.class, handler.eventType());

        handler.handle(new MovementAdvanced(
                STATION_ID, 1, MEMBER_ID, 99, "Helm", "Tausch angekündigt", MEMBER_ID, StepActor.STATION, null));

        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.INVENTORY_MANAGER)
                        .except(MEMBER_ID),
                NotificationType.MOVEMENT_ADVANCED,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void movementAdvancedTellsTheMemberWhenTheirStepIsNext() {
        var handler = new MovementAdvancedHandler(notifier);

        handler.handle(new MovementAdvanced(
                STATION_ID, 1, MEMBER_ID, 99, "Helm", "Ersatz erhalten", 30, StepActor.MEMBER, null));

        verifyTold(
                StationAudience.member(MEMBER_ID).except(30),
                NotificationType.MOVEMENT_ADVANCED,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void aChainThatHasEndedTellsTheMemberItConcerned() {
        var handler = new MovementAdvancedHandler(notifier);

        handler.handle(new MovementAdvanced(STATION_ID, 1, MEMBER_ID, 99, "Helm", "Ersatz ausgegeben", 30, null, null));

        verifyTold(
                StationAudience.member(MEMBER_ID).except(30),
                NotificationType.MOVEMENT_ADVANCED,
                Delivery.ONCE_WHILE_UNREAD);
    }

    /** A member step with nobody named falls back to the team that runs the inventory. */
    @Test
    void aMemberStepWithoutAMemberTellsTheTeam() {
        var handler = new MovementAdvancedHandler(notifier);

        handler.handle(
                new MovementAdvanced(STATION_ID, 1, null, 99, "Helm", "Ersatz erhalten", 30, StepActor.MEMBER, null));

        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.INVENTORY_MANAGER)
                        .except(30),
                NotificationType.MOVEMENT_ADVANCED,
                Delivery.ONCE_WHILE_UNREAD);
    }

    /**
     * An owner that does not run here cannot be told anything, so the station that stands in for it
     * is who hears. Nothing is addressed to a party that could never receive it.
     */
    @Test
    void anOwnerStepIsAnnouncedToTheStationStandingInForIt() {
        var handler = new MovementAdvancedHandler(notifier);

        handler.handle(new MovementAdvanced(
                STATION_ID, 1, MEMBER_ID, 99, "Helm", "An den Träger geschickt", MEMBER_ID, StepActor.OWNER, null));

        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.INVENTORY_MANAGER)
                        .except(MEMBER_ID),
                NotificationType.MOVEMENT_ADVANCED,
                Delivery.ONCE_WHILE_UNREAD);
    }

    /**
     * An owner that is a cluster on this instance can be told, and is the only party that can answer
     * the step. The station hears nothing: it has nothing to do but wait.
     */
    @Test
    void anOwnerStepOnClusterGearIsAnnouncedToTheCluster() {
        var handler = new MovementAdvancedHandler(notifier);

        handler.handle(new MovementAdvanced(
                STATION_ID, 1, MEMBER_ID, 99, "Helm", "An den Träger geschickt", MEMBER_ID, StepActor.OWNER, 7));

        verify(notifier)
                .notify(
                        eq(ClusterAudience.holders(7, ClusterPermission.CLUSTER_INVENTORY_MANAGER)),
                        eq(NotificationType.MOVEMENT_ADVANCED),
                        argThat(data -> "cluster-movements".equals(data.link().route())),
                        eq(Delivery.ONCE_WHILE_UNREAD));
        verify(notifier).notify(any(), any(), any(), any());
    }

    /**
     * A chain that ends with nobody at either end tells nobody. It is the one case where the right
     * answer is silence rather than a fallback recipient.
     */
    @Test
    void aChainThatEndsWithNoMemberTellsNobody() {
        var handler = new MovementAdvancedHandler(notifier);

        handler.handle(new MovementAdvanced(STATION_ID, 1, null, 99, "Helm", "Eingelagert", 30, null, null));

        var audience = org.mockito.ArgumentCaptor.forClass(Audience.class);
        verify(notifier).notify(audience.capture(), any(), any(), any());
        assertTrue(((StationAudience) audience.getValue()).reachesNobody());
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
                false,
                false);
    }

    /**
     * Being told that something is coming is a different sentence from being asked to acknowledge a step,
     * so it reaches the station as its own message and lands on the movement carrying it.
     */
    @Test
    void clusterItemIssuedTellsTheStationWhatIsOnItsWay() {
        var handler = new ClusterItemIssuedHandler(notifier);
        assertEquals(ClusterItemIssued.class, handler.eventType());

        handler.handle(new ClusterItemIssued(STATION_ID, 5, "Kreisverband Musterstadt", "Jacke"));

        verify(notifier)
                .notify(
                        eq(StationAudience.holders(STATION_ID, StationPermission.INVENTORY_MANAGER)),
                        eq(NotificationType.CLUSTER_ITEM_ISSUED),
                        argThat(data ->
                                "inventory-movement-detail".equals(data.link().route())),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    @Test
    void clusterItemLostTellsTheClusterAndNamesTheStation() {
        var stationRepository = mock(StationRepository.class);
        var handler = new ClusterItemLostHandler(notifier, stationRepository);
        assertEquals(ClusterItemLost.class, handler.eventType());

        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(station("JF Nachbarstadt")));

        handler.handle(new ClusterItemLost(7, "Helm", STATION_ID));

        verify(notifier)
                .notify(
                        eq(ClusterAudience.holders(7, ClusterPermission.CLUSTER_INVENTORY_MANAGER)),
                        eq(NotificationType.CLUSTER_ITEM_LOST),
                        argThat(data -> "cluster-inventory".equals(data.link().route())),
                        eq(Delivery.ONCE_WHILE_UNREAD));
    }

    /** A station that has gone since the report still leaves a message worth reading. */
    @Test
    void clusterItemLostSurvivesAStationThatIsNoLongerThere() {
        var stationRepository = mock(StationRepository.class);
        var handler = new ClusterItemLostHandler(notifier, stationRepository);

        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.empty());

        handler.handle(new ClusterItemLost(7, "Helm", STATION_ID));

        verifyTold(
                ClusterAudience.holders(7, ClusterPermission.CLUSTER_INVENTORY_MANAGER),
                NotificationType.CLUSTER_ITEM_LOST,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void movementDeclinedTellsBothEnds() {
        var handler = new MovementDeclinedHandler(notifier);
        assertEquals(MovementDeclined.class, handler.eventType());

        handler.handle(new MovementDeclined(STATION_ID, 1, MEMBER_ID, 99, "Helm", "Kein Ersatz", 30));

        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.INVENTORY_MANAGER)
                        .and(StationAudience.member(MEMBER_ID))
                        .except(30),
                NotificationType.MOVEMENT_DECLINED,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void procurementCreatedNotifiesMember() {
        var handler = new ProcurementCreatedHandler(notifier);
        assertEquals(ProcurementCreated.class, handler.eventType());

        handler.handle(new ProcurementCreated(STATION_ID, MEMBER_ID, 99, "Schlauch"));

        verifyTold(StationAudience.member(MEMBER_ID), NotificationType.PROCUREMENT_REQUESTED, Delivery.EVERY_TIME);
    }

    @Test
    void procurementFulfilledNotifiesMember() {
        var handler = new ProcurementFulfilledHandler(notifier);
        assertEquals(ProcurementFulfilled.class, handler.eventType());

        handler.handle(new ProcurementFulfilled(STATION_ID, MEMBER_ID, 99, "Schlauch"));

        verifyTold(StationAudience.member(MEMBER_ID), NotificationType.PROCUREMENT_FULFILLED, Delivery.EVERY_TIME);
    }

    @Test
    void membersAddedToGroupNotifiesAllMembers() {
        var handler = new MembersAddedToGroupHandler(notifier, memberRepository);
        assertEquals(MembersAddedToGroup.class, handler.eventType());

        var memberIds = List.of(10, 11, 12);
        handler.handle(new MembersAddedToGroup(STATION_ID, "Anfänger", memberIds, null));

        verifyTold(StationAudience.members(memberIds), NotificationType.MEMBER_ADDED_TO_GROUP, Delivery.EVERY_TIME);
    }

    @Test
    void lendingRequestedNotifiesOwningStationManagers() {
        var handler = new LendingRequestedHandler(notifier);
        assertEquals(LendingRequested.class, handler.eventType());

        int owningStationId = 2;
        handler.handle(new LendingRequested(STATION_ID, owningStationId, 99, "Feuerwehr Ost", "2x Schlauch"));

        verifyTold(
                StationAudience.holders(owningStationId, StationPermission.INVENTORY_MANAGER),
                NotificationType.LENDING_NEW_REQUEST,
                Delivery.EVERY_TIME);
    }

    @Test
    void lendingStatusChangedNotifiesTargetStation() {
        var handler = new LendingStatusChangedHandler(notifier);
        assertEquals(LendingStatusChanged.class, handler.eventType());

        int targetStationId = 3;
        handler.handle(new LendingStatusChanged(
                STATION_ID,
                targetStationId,
                99,
                NotificationType.LENDING_STATUS_CHANGE,
                "Feuerwehr West",
                LendingStatus.APPROVED));

        verifyTold(
                StationAudience.holders(targetStationId, StationPermission.INVENTORY_MANAGER),
                NotificationType.LENDING_STATUS_CHANGE,
                Delivery.EVERY_TIME);
    }

    @Test
    void lendingMessageSentNotifiesTargetStation() {
        var handler = new LendingMessageSentHandler(notifier);
        assertEquals(LendingMessageSent.class, handler.eventType());

        int targetStationId = 3;
        handler.handle(new LendingMessageSent(STATION_ID, targetStationId, 99, "Feuerwehr West", "Max Mustermann"));

        verifyTold(
                StationAudience.holders(targetStationId, StationPermission.INVENTORY_MANAGER),
                NotificationType.LENDING_NEW_MESSAGE,
                Delivery.EVERY_TIME);
    }

    @Test
    void registrationDeadlineExpiredNotifiesEventManagers() {
        var handler = new RegistrationDeadlineExpiredHandler(notifier);
        assertEquals(RegistrationDeadlineExpired.class, handler.eventType());

        handler.handle(new RegistrationDeadlineExpired(STATION_ID, 42, "Übungsabend", 5));

        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.EVENT_MANAGER),
                NotificationType.REGISTRATION_DEADLINE_EXPIRED,
                Delivery.EVERY_TIME);
    }

    @Test
    void boardTicketChangedNotifiesWatchers() {
        var handler = new BoardTicketChangedHandler(notifier);
        assertEquals(BoardTicketChanged.class, handler.eventType());

        handler.handle(new BoardTicketChanged(
                STATION_ID, 1, 42, "DEV", 7, "Dev Board", "DEV-7", "Neuer Kommentar", MEMBER_ID, List.of(20, 21)));

        verifyTold(
                StationAudience.members(List.of(20, 21)).except(MEMBER_ID),
                NotificationType.BOARD_TICKET_UPDATE,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void boardTicketChangedSkipsEmptyWatchers() {
        var handler = new BoardTicketChangedHandler(notifier);

        handler.handle(new BoardTicketChanged(
                STATION_ID, 1, 42, "DEV", 7, "Dev Board", "DEV-7", "Update", MEMBER_ID, List.of()));

        verifyTold(
                StationAudience.members(List.of()).except(MEMBER_ID),
                NotificationType.BOARD_TICKET_UPDATE,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Test
    void storageWarningNotifiesStationManagers() {
        var handler = new StorageWarningHandler(notifier);
        var event = new StorageWarningEvent(STATION_ID, 85, 4_500_000_000L, 5_368_709_120L);

        assertEquals(StorageWarningEvent.class, handler.eventType());

        handler.handle(event);

        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.STATION_MANAGER),
                NotificationType.STORAGE_WARNING,
                Delivery.EVERY_TIME);
    }

    @Test
    void waitlistPublicRegistrationHandlerNotifiesWaitlistEditRole() {
        var handler = new WaitlistPublicRegistrationHandler(notifier);
        var event = new WaitlistPublicRegistration(STATION_ID, "Max Müller", "Warteliste 2026");

        assertEquals(WaitlistPublicRegistration.class, handler.eventType());

        handler.handle(event);

        verifyTold(
                StationAudience.holders(STATION_ID, StationPermission.WAITLIST_EDIT),
                NotificationType.WAITLIST_PUBLIC_REGISTRATION,
                Delivery.EVERY_TIME);
    }
}
