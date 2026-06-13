/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.restriction.RestrictionType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class BulkMentionedInCommentHandler implements DomainEventHandler<BulkMentionedInComment> {
    private final NotificationService notificationService;
    private final MemberGroupRepository memberGroupRepository;
    private final EventRepository eventRepository;
    private final StationMemberRepository stationMemberRepository;
    private final RestrictionRepository restrictionRepository;

    @Inject
    public BulkMentionedInCommentHandler(
            NotificationService notificationService,
            MemberGroupRepository memberGroupRepository,
            EventRepository eventRepository,
            StationMemberRepository stationMemberRepository,
            RestrictionRepository restrictionRepository) {
        this.notificationService = notificationService;
        this.memberGroupRepository = memberGroupRepository;
        this.eventRepository = eventRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.restrictionRepository = restrictionRepository;
    }

    @Override
    public Class<BulkMentionedInComment> eventType() {
        return BulkMentionedInComment.class;
    }

    @Override
    public void handle(BulkMentionedInComment event) {
        Set<Integer> memberIds =
                switch (event.mentionType()) {
                    case GROUP ->
                        memberGroupRepository.findMembers(event.mentionTargetId()).stream()
                                .map(StationMember::id)
                                .collect(Collectors.toCollection(HashSet::new));
                    case EVENT -> resolveEventMembers(event);
                    case REGISTERED ->
                        resolveRegistrationsByStatus(event.mentionTargetId(), RegistrationStatus.ACCEPTED);
                    case DECLINED -> resolveRegistrationsByStatus(event.mentionTargetId(), RegistrationStatus.DECLINED);
                };

        if (event.mentionType() != MentionType.GROUP) {
            addGuardians(memberIds);
        }

        var link =
                switch (event.entityType()) {
                    case NEWS -> new NotificationData.NotificationLink("news-detail", Map.of("id", event.entityId()));
                    case BOARD_TICKET ->
                        new NotificationData.NotificationLink("ticket-detail", Map.of("ticketId", event.entityId()));
                    case KB -> new NotificationData.NotificationLink("kb-file", Map.of("id", event.entityId()));
                    case EVENT -> new NotificationData.NotificationLink("event-detail", Map.of("id", event.entityId()));
                };

        var data = NotificationData.of(
                new NotificationParams.CommentMention(event.entityTitle(), event.authorName(), event.preview()), link);

        for (int memberId : memberIds) {
            if (event.authorMemberId() == null || memberId != event.authorMemberId()) {
                notificationService.notifyIfAbsent(memberId, NotificationType.COMMENT_MENTION, data);
            }
        }
    }

    private Set<Integer> resolveEventMembers(BulkMentionedInComment event) {
        var stationEvent = eventRepository.findById(event.mentionTargetId()).orElse(null);
        if (stationEvent == null) return Set.of();

        var allRegs = eventRepository.findAllRegistrations(event.mentionTargetId());
        var declinedIds = allRegs.stream()
                .filter(r -> r.status() == RegistrationStatus.DECLINED)
                .map(EventRegistration::memberId)
                .collect(Collectors.toSet());

        if (stationEvent.requiresRegistration()) {
            var ids = new HashSet<Integer>();
            for (var r : allRegs) {
                if (r.status() != RegistrationStatus.DECLINED) {
                    ids.add(r.memberId());
                }
            }
            return ids;
        }

        // No registration required — notify all members who can see the event, minus declined
        var eligible = restrictionRepository.findMembersPassingRestriction(
                RestrictionType.EVENT, stationEvent.id(), stationEvent.stationId());
        if (eligible.isEmpty()) {
            var ids = stationMemberRepository.findByStation(stationEvent.stationId(), false).stream()
                    .map(StationMember::id)
                    .collect(Collectors.toCollection(HashSet::new));
            ids.removeAll(declinedIds);
            return ids;
        }
        var ids = new HashSet<>(eligible);
        ids.removeAll(declinedIds);
        return ids;
    }

    private Set<Integer> resolveRegistrationsByStatus(int eventId, RegistrationStatus status) {
        return eventRepository.findAllRegistrations(eventId).stream()
                .filter(r -> r.status() == status)
                .map(EventRegistration::memberId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void addGuardians(Set<Integer> memberIds) {
        var guardianIds = new HashSet<Integer>();
        for (int memberId : memberIds) {
            stationMemberRepository.findManagers(memberId).stream()
                    .map(StationMember::id)
                    .forEach(guardianIds::add);
        }
        memberIds.addAll(guardianIds);
    }
}
