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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FormPublishedHandler implements DomainEventHandler<FormPublished> {
    private final NotificationService notificationService;

    @Inject
    public FormPublishedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<FormPublished> eventType() {
        return FormPublished.class;
    }

    @Override
    public void handle(FormPublished event) {
        notificationService.notifyStation(
                event.stationId(),
                NotificationType.NEW_FORM,
                NotificationData.of(
                        new NotificationParams.NewForm(event.formTitle()), NotificationLinks.form(event.formId())));
    }
}
