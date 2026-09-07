/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventCreated;
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
public class EventCreatedHandler implements DomainEventHandler<EventCreated> {
    private final NotificationService notificationService;
    private final RestrictionService restrictionService;

    @Inject
    public EventCreatedHandler(NotificationService notificationService, RestrictionService restrictionService) {
        this.notificationService = notificationService;
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
     */
    @Override
    public void handle(EventCreated event) {
        var e = event.event();
        // Pass the full description through - the feed renderer applies word-boundary
        // truncation via NotificationService.truncateSnippet on the way out so we don't
        // mangle the text at a fixed 80-char cut here.
        String description = e.description() == null ? "" : e.description();
        var data = NotificationData.of(
                new NotificationParams.NewEvent(e.name(), description), NotificationLinks.event(e.id()));

        var viewers =
                restrictionService.findMembersPassingRestriction(RestrictionType.EVENT_VIEW, e.id(), event.stationId());
        if (viewers.isEmpty()) {
            notificationService.notifyStation(event.stationId(), NotificationType.NEW_EVENT, data);
            return;
        }
        notificationService.notifyMembers(viewers, NotificationType.NEW_EVENT, data);
    }
}
