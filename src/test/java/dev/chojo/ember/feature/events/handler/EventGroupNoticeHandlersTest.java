/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.events.EventGroupCancelled;
import dev.chojo.ember.event.events.EventGroupDeadlineExpired;
import dev.chojo.ember.event.events.EventGroupRegistrationStatusChanged;
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

/** The three things said about an event group: each names the event group and links to its page. */
class EventGroupNoticeHandlersTest {
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
        new EventGroupRegistrationStatusHandler(notifier)
                .handle(new EventGroupRegistrationStatusChanged(
                        3, 70, "Jugendflamme 1", 11, "Anna", RegistrationStatus.ACCEPTED));

        var data = sent(NotificationType.EVENT_REGISTRATION_STATUS, 2);
        assertEquals("Jugendflamme 1", ((NotificationParams.EventRegistrationStatus) data.params()).eventName());
        assertEquals("event-group", data.link().route());
        assertEquals(Map.of("eventGroupId", 70), data.link().routeParams());
    }

    @Test
    void aCalledOffEventGroupTellsItsPeopleOnceAndNobodyWhenNobodyIsSignedUp() {
        var handler = new EventGroupCancelledHandler(notifier);

        handler.handle(new EventGroupCancelled(3, 70, "Jugendflamme 1", null, List.of()));
        verify(notifier, never()).notify(any(), any(), any(), any(Delivery.class));

        handler.handle(new EventGroupCancelled(3, 70, "Jugendflamme 1", "Krank", List.of(11, 12)));
        var data = sent(NotificationType.EVENT_CANCELLED, 1);
        assertEquals("Krank", ((NotificationParams.EventCancelled) data.params()).reason());
    }

    @Test
    void aPassedDeadlineIsToldToWhoeverRunsEvents() {
        new EventGroupDeadlineExpiredHandler(notifier)
                .handle(new EventGroupDeadlineExpired(3, 70, "Jugendflamme 1", 2));

        var data = sent(NotificationType.REGISTRATION_DEADLINE_EXPIRED, 1);
        assertEquals(2, ((NotificationParams.RegistrationDeadlineExpired) data.params()).pendingCount());
    }
}
