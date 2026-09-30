/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventCreated;
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
public class EventCreatedHandler implements DomainEventHandler<EventCreated> {
    private final Notifier notifier;
    private final RestrictionService restrictionService;

    @Inject
    public EventCreatedHandler(Notifier notifier, RestrictionService restrictionService) {
        this.notifier = notifier;
        this.restrictionService = restrictionService;
    }

    @Override
    public Class<EventCreated> eventType() {
        return EventCreated.class;
    }

    /**
     * Announces a new appointment to the people who may know it exists.
     *
     * <p>An appointment narrowed for registration still reaches everybody: they see it in the
     * calendar and simply cannot answer it. One narrowed for visibility reaches only its audience,
     * because a notification about something that is absent from every list is a dead end.
     *
     * <p>The whole description is passed on; the feed cuts it at a word boundary on the way out
     * through {@link dev.chojo.ember.feature.notifications.service.NotificationText#truncateSnippet}.
     */
    @Override
    public void handle(EventCreated event) {
        var e = event.event();
        String description = e.description() == null ? "" : e.description();
        var data = NotificationData.of(
                new NotificationParams.NewEvent(e.name(), description), NotificationLinks.event(e.id()));

        var viewers =
                restrictionService.findMembersPassingRestriction(RestrictionType.EVENT_VIEW, e.id(), event.stationId());
        notifier.notify(
                StationAudience.visibleTo(event.stationId(), viewers),
                NotificationType.NEW_EVENT,
                data,
                Delivery.EVERY_TIME);
    }
}
