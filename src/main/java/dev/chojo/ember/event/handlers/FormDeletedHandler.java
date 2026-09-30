/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.FormDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FormDeletedHandler implements DomainEventHandler<FormDeleted> {
    private final Notifier notifier;

    @Inject
    public FormDeletedHandler(Notifier notifier) {
        this.notifier = notifier;
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
        notifier.withdrawAll(NotificationLinks.form(event.formId()));
    }
}
