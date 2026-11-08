/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.events.WaitlistTrialEnded;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** A trial the family ends leaves nothing on the list, so whoever looks after the lists is told. */
class WaitlistTrialEndedHandlerTest {

    private Notifier notifier;
    private WaitlistTrialEndedHandler handler;

    @BeforeEach
    void setup() {
        notifier = mock(Notifier.class);
        handler = new WaitlistTrialEndedHandler(notifier);
    }

    @Test
    void itHandlesItsOwnEvent() {
        assertEquals(WaitlistTrialEnded.class, handler.eventType());
    }

    @Test
    void whoeverEditsTheListsIsToldWhoseTrialEnded() {
        handler.handle(new WaitlistTrialEnded(7, "Max Müller", "Schnupperstunde"));

        var captor = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier)
                .notify(
                        eq(StationAudience.holders(7, StationPermission.WAITLIST_EDIT)),
                        eq(NotificationType.WAITLIST_TRIAL_ENDED),
                        captor.capture(),
                        eq(Delivery.EVERY_TIME));

        var params = (NotificationParams.WaitlistTrialEnded) captor.getValue().params();
        assertEquals("Max Müller", params.childName());
        assertEquals("Schnupperstunde", params.listName());
        assertEquals("waiting-lists", captor.getValue().link().route());
    }
}
