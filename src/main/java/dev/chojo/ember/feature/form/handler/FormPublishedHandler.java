/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.FormPublished;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FormPublishedHandler implements DomainEventHandler<FormPublished> {
    private final Notifier notifier;
    private final RestrictionService restrictionService;

    @Inject
    public FormPublishedHandler(Notifier notifier, RestrictionService restrictionService) {
        this.notifier = notifier;
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
        notifier.notify(
                StationAudience.visibleTo(
                        event.stationId(),
                        restrictionService.findMembersPassingRestriction(
                                RestrictionType.FORM, event.formId(), event.stationId())),
                NotificationType.NEW_FORM,
                NotificationData.of(
                        new NotificationParams.NewForm(event.formTitle()), NotificationLinks.form(event.formId())),
                Delivery.EVERY_TIME);
    }
}
