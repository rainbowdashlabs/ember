/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class BulkMentionedInCommentHandler implements DomainEventHandler<BulkMentionedInComment> {
    private final Notifier notifier;
    private final MemberGroupRepository memberGroupRepository;
    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final RestrictionService restrictionService;

    @Inject
    public BulkMentionedInCommentHandler(
            Notifier notifier,
            MemberGroupRepository memberGroupRepository,
            EventRepository eventRepository,
            EventRegistrationRepository registrationRepository,
            StationMemberRepository stationMemberRepository,
            RestrictionService restrictionService) {
        this.notifier = notifier;
        this.memberGroupRepository = memberGroupRepository;
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.restrictionService = restrictionService;
    }

    @Override
    public Class<BulkMentionedInComment> eventType() {
        return BulkMentionedInComment.class;
    }

    @Override
    public void handle(BulkMentionedInComment event) {
        Set<Integer> memberIds =
                switch (event.mentionType()) {
                    case GROUP -> resolveGroupMembers(event);
                    case EVENT -> resolveEventMembers(event);
                    case REGISTERED -> resolveRegistrationsByStatus(event, RegistrationStatus.ACCEPTED);
                    case DECLINED -> resolveRegistrationsByStatus(event, RegistrationStatus.DECLINED);
                };

        var audience = event.mentionType() == MentionType.GROUP
                ? StationAudience.members(memberIds)
                : StationAudience.household(memberIds);

        var data = NotificationData.of(
                new NotificationParams.CommentMention(event.entityTitle(), event.authorName(), event.preview()),
                event.link());

        notifier.notify(
                audience.except(event.authorMemberId()),
                NotificationType.COMMENT_MENTION,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }

    /**
     * The members of a mentioned group, when the group belongs to the station the comment was
     * written in.
     *
     * <p>A mention names a group by its id, and the ids run across the whole instance, so without
     * the station test a member of one station addresses another station's group and everyone in it
     * is handed the comment.
     */
    private Set<Integer> resolveGroupMembers(BulkMentionedInComment event) {
        var group = memberGroupRepository.findById(event.mentionTargetId()).orElse(null);
        if (group == null || group.stationId() != event.stationId()) return new HashSet<>();
        return memberGroupRepository.findMembers(group.id()).stream()
                .map(StationMember::id)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * The members a mentioned event reaches: those registered where it requires registration, and
     * otherwise everybody who can see it, minus those who declined.
     */
    private Set<Integer> resolveEventMembers(BulkMentionedInComment event) {
        var stationEvent = eventRepository.findById(event.mentionTargetId()).orElse(null);
        if (stationEvent == null || stationEvent.stationId() != event.stationId()) return new HashSet<>();

        var allRegs = registrationRepository.findByEvent(event.mentionTargetId());
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

        var eligible = restrictionService.findMembersPassingRestriction(
                RestrictionType.EVENT_VIEW, stationEvent.id(), stationEvent.stationId());
        if (eligible.isEmpty()) {
            var ids = stationMemberRepository.findByStation(stationEvent.stationId(), false).stream()
                    .map(StationMember::id)
                    .collect(Collectors.toCollection(HashSet::new));
            ids.removeAll(declinedIds);
            return ids;
        }
        var ids = new HashSet<>(eligible.get());
        ids.removeAll(declinedIds);
        return ids;
    }

    /**
     * Those registered for a mentioned event with the given status, when the event belongs to the
     * station the comment was written in.
     */
    private Set<Integer> resolveRegistrationsByStatus(BulkMentionedInComment event, RegistrationStatus status) {
        var stationEvent = eventRepository.findById(event.mentionTargetId()).orElse(null);
        if (stationEvent == null || stationEvent.stationId() != event.stationId()) return new HashSet<>();
        return registrationRepository.findByEvent(stationEvent.id()).stream()
                .filter(registration -> registration.status() == status)
                .map(EventRegistration::memberId)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
