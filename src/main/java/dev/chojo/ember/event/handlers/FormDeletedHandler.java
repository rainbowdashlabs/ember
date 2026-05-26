/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.FormDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class FormDeletedHandler implements DomainEventHandler<FormDeleted> {
    private final NotificationService notificationService;

    @Inject
    public FormDeletedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<FormDeleted> eventType() {
        return FormDeleted.class;
    }

    @Override
    public void handle(FormDeleted event) {
        notificationService.deleteByTypeContaining(
                NotificationType.NEW_FORM,
                NotificationData.of(
                                new NotificationParams.NewForm(null),
                                new NotificationData.NotificationLink(
                                        "forms-fill", Map.of("id", String.valueOf(event.formId()))))
                        .toJson());
    }
}
