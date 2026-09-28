/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.FormPublished;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FormPublishedHandler implements DomainEventHandler<FormPublished> {
    private final NotificationService notificationService;
    private final RestrictionService restrictionService;

    @Inject
    public FormPublishedHandler(NotificationService notificationService, RestrictionService restrictionService) {
        this.notificationService = notificationService;
        this.restrictionService = restrictionService;
    }

    @Override
    public Class<FormPublished> eventType() {
        return FormPublished.class;
    }

    /**
     * Announces a published form to the members who may open it.
     */
    @Override
    public void handle(FormPublished event) {
        notificationService.notifyAudience(
                event.stationId(),
                restrictionService.findMembersPassingRestriction(
                        RestrictionType.FORM, event.formId(), event.stationId()),
                NotificationType.NEW_FORM,
                NotificationData.of(
                        new NotificationParams.NewForm(event.formTitle()), NotificationLinks.form(event.formId())));
    }
}
