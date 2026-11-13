/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.events.CourseCancelled;
import dev.chojo.ember.event.events.CourseDeadlineExpired;
import dev.chojo.ember.event.events.CourseRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The three things said about a course: each names the course and links to its page. */
class CourseNoticeHandlersTest {
    private Notifier notifier;

    @BeforeEach
    void wire() {
        notifier = mock(Notifier.class);
    }

    private NotificationData sent(NotificationType type, int times) {
        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier, times(times)).notify(any(), eq(type), data.capture(), any(Delivery.class));
        return data.getValue();
    }

    @Test
    void aDecisionReachesTheMemberAndWhoeverRunsEvents() {
        new CourseRegistrationStatusHandler(notifier)
                .handle(new CourseRegistrationStatusChanged(
                        3, 70, "Jugendflamme 1", 11, "Anna", RegistrationStatus.ACCEPTED));

        var data = sent(NotificationType.EVENT_REGISTRATION_STATUS, 2);
        assertEquals("Jugendflamme 1", ((NotificationParams.EventRegistrationStatus) data.params()).eventName());
        assertEquals("event-course", data.link().route());
        assertEquals(Map.of("courseId", 70), data.link().routeParams());
    }

    @Test
    void aCalledOffCourseTellsItsPeopleOnceAndNobodyWhenNobodyIsSignedUp() {
        var handler = new CourseCancelledHandler(notifier);

        handler.handle(new CourseCancelled(3, 70, "Jugendflamme 1", null, List.of()));
        verify(notifier, never()).notify(any(), any(), any(), any(Delivery.class));

        handler.handle(new CourseCancelled(3, 70, "Jugendflamme 1", "Krank", List.of(11, 12)));
        var data = sent(NotificationType.EVENT_CANCELLED, 1);
        assertEquals("Krank", ((NotificationParams.EventCancelled) data.params()).reason());
    }

    @Test
    void aPassedDeadlineIsToldToWhoeverRunsEvents() {
        new CourseDeadlineExpiredHandler(notifier).handle(new CourseDeadlineExpired(3, 70, "Jugendflamme 1", 2));

        var data = sent(NotificationType.REGISTRATION_DEADLINE_EXPIRED, 1);
        assertEquals(2, ((NotificationParams.RegistrationDeadlineExpired) data.params()).pendingCount());
    }
}
