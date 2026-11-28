/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.events.EventGroupCancelled;
import dev.chojo.ember.event.events.EventGroupRegistrationStatusChanged;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The three notices sent about an event group: each names the event group and links to its page. */
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
    void aChangeReachesTheMemberAndWhoeverRunsEvents() {
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

        handler.handle(new EventGroupCancelled(3, 70, "Jugendflamme 1", null, List.of(), CancellationCause.MANUAL));
        verify(notifier, never()).notify(any(), any(), any(), any(Delivery.class));

        handler.handle(
                new EventGroupCancelled(3, 70, "Jugendflamme 1", "Krank", List.of(11, 12), CancellationCause.MANUAL));
        var data = sent(NotificationType.EVENT_CANCELLED, 1);
        assertEquals("Krank", ((NotificationParams.EventCancelled) data.params()).reason());
    }

    @Test
    void anEventGroupCalledOffForTooFewSignUpsSaysSo() {
        new EventGroupCancelledHandler(notifier)
                .handle(new EventGroupCancelled(
                        3, 70, "Jugendflamme 1", null, List.of(11), CancellationCause.THRESHOLD));

        var params = (NotificationParams.EventCancelled)
                sent(NotificationType.EVENT_CANCELLED, 1).params();
        assertEquals(CancellationCause.THRESHOLD, params.cause());
        assertEquals(Boolean.TRUE, params.eventGroup());
    }

    @Test
    void aPassedDeadlineIsToldToWhoeverRunsEvents() {
        new RegistrationDeadlineExpiredHandler(notifier)
                .handle(new RegistrationDeadlineExpired(
                        3, "Jugendflamme 1", 2, null, NotificationLinks.eventGroup(70)));

        var data = sent(NotificationType.REGISTRATION_DEADLINE_EXPIRED, 1);
        var params = (NotificationParams.RegistrationDeadlineExpired) data.params();
        assertEquals(2, params.pendingCount());
        assertNull(params.eventDate(), "an event group closes once");
        assertEquals(NotificationLinks.eventGroup(70), data.link());
    }

    @Test
    void movingUpAndWaitingReachOnlyTheMember() {
        new EventGroupRegistrationStatusHandler(notifier)
                .handle(new EventGroupRegistrationStatusChanged(
                        3, 70, "Jugendflamme 1", 11, "Anna", RegistrationStatus.ACCEPTED, null, true, true, false));
        var movedUp = (NotificationParams.EventRegistrationStatus)
                sent(NotificationType.EVENT_REGISTRATION_STATUS, 1).params();
        assertTrue(movedUp.movedUp());
        assertTrue(movedUp.shortNotice());

        new EventRegistrationStatusHandler(notifier)
                .handle(new EventRegistrationStatusChanged(
                        3, 9, "Atemschutz", 12, "Ben", RegistrationStatus.QUEUED, 2, false, false, false));
        var waiting = (NotificationParams.EventRegistrationStatus)
                sent(NotificationType.EVENT_REGISTRATION_STATUS, 2).params();
        assertEquals(2, waiting.queuePosition());
    }

    /** Whoever accepted or denied a sign-up knows, so only the member hears of it. */
    @Test
    void aDecisionReachesOnlyTheMember() {
        new EventGroupRegistrationStatusHandler(notifier)
                .handle(EventGroupRegistrationStatusChanged.decision(
                        3, 70, "Jugendflamme 1", 11, "Anna", RegistrationStatus.ACCEPTED));
        sent(NotificationType.EVENT_REGISTRATION_STATUS, 1);
        verify(notifier).notify(eq(StationAudience.member(11)), any(), any(), any(Delivery.class));

        new EventRegistrationStatusHandler(notifier)
                .handle(EventRegistrationStatusChanged.decision(
                        3, 9, "Atemschutz", 12, "Ben", RegistrationStatus.DENIED));
        sent(NotificationType.EVENT_REGISTRATION_STATUS, 2);
        verify(notifier).notify(eq(StationAudience.member(12)), any(), any(), any(Delivery.class));
    }
}
