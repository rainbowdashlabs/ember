/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MembersAddedToGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MembersAddedToGroupHandler implements DomainEventHandler<MembersAddedToGroup> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public MembersAddedToGroupHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<MembersAddedToGroup> eventType() {
        return MembersAddedToGroup.class;
    }

    @Override
    public void handle(MembersAddedToGroup event) {
        String addedByName = event.addedByMemberId() == null
                ? null
                : stationMemberRepository
                        .findById(event.addedByMemberId())
                        .map(StationMember::displayName)
                        .orElse(null);
        var data = NotificationData.of(
                new NotificationParams.MemberAddedToGroup(event.groupName(), addedByName),
                new NotificationData.NotificationLink("dashboard-overview"));
        notificationService.notifyMembers(event.memberIds(), NotificationType.MEMBER_ADDED_TO_GROUP, data);
    }
}
