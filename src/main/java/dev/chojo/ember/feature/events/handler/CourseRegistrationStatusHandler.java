/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.CourseRegistrationStatusChanged;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells a member, and whoever runs events, that a course sign-up changed: once for the course, not
 * once for every part chosen with it.
 */
@Singleton
public class CourseRegistrationStatusHandler implements DomainEventHandler<CourseRegistrationStatusChanged> {
    private final Notifier notifier;

    @Inject
    public CourseRegistrationStatusHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<CourseRegistrationStatusChanged> eventType() {
        return CourseRegistrationStatusChanged.class;
    }

    @Override
    public void handle(CourseRegistrationStatusChanged event) {
        var data = NotificationData.of(
                new NotificationParams.EventRegistrationStatus(
                        event.memberName(), event.courseName(), event.newStatus(), null),
                NotificationLinks.course(event.courseId()));
        notifier.notify(
                StationAudience.member(event.memberId()),
                NotificationType.EVENT_REGISTRATION_STATUS,
                data,
                Delivery.EVERY_TIME);
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.EVENT_MANAGER)
                        .except(event.memberId()),
                NotificationType.EVENT_REGISTRATION_STATUS,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
