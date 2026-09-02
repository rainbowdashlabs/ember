/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.FormDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

    /**
     * Takes the invitation to fill the form with it, read or not, because the form it opens is gone.
     */
    @Override
    public void handle(FormDeleted event) {
        notificationService.deleteAllPointingAt(NotificationLinks.form(event.formId()));
    }
}
