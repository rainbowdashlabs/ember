/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureItemChecked;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProcedureItemCheckedHandler implements DomainEventHandler<ProcedureItemChecked> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ProcedureItemCheckedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<ProcedureItemChecked> eventType() {
        return ProcedureItemChecked.class;
    }

    @Override
    public void handle(ProcedureItemChecked event) {
        String checkedByName = stationMemberRepository
                .findById(event.checkedByMemberId())
                .map(StationMember::displayName)
                .orElse("?");
        var data = NotificationData.of(
                new NotificationParams.ProcedureItemCheckedParams(
                        event.procedureName(), event.itemTitle(), checkedByName),
                new NotificationData.NotificationLink("procedures"));
        notificationService.notifyMembersIfAbsent(
                event.assigneeMemberIds(), NotificationType.PROCEDURE_ITEM_CHECKED, data, event.checkedByMemberId());
    }
}
