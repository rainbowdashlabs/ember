/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureItemChecked;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class ProcedureItemCheckedHandler implements DomainEventHandler<ProcedureItemChecked> {
    private final Notifier notifier;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ProcedureItemCheckedHandler(Notifier notifier, StationMemberRepository stationMemberRepository) {
        this.notifier = notifier;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<ProcedureItemChecked> eventType() {
        return ProcedureItemChecked.class;
    }

    @Override
    public void handle(ProcedureItemChecked event) {
        String checkedByName = stationMemberRepository.displayNameOr(event.checkedByMemberId(), "?");
        var data = NotificationData.of(
                new NotificationParams.ProcedureItemCheckedParams(
                        event.procedureName(), event.itemTitle(), checkedByName),
                new NotificationData.NotificationLink("procedure-detail", Map.of("id", event.procedureId())));
        notifier.notify(
                StationAudience.members(event.partyMemberIds()).except(event.checkedByMemberId()),
                NotificationType.PROCEDURE_ITEM_CHECKED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
