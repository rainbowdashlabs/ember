/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.events.WaitlistInvitationDatesGone;
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

/** An invitation left without a date asks the station for its next step, so whoever looks after the lists is told. */
class WaitlistInvitationDatesGoneHandlerTest {

    private Notifier notifier;
    private WaitlistInvitationDatesGoneHandler handler;

    @BeforeEach
    void setup() {
        notifier = mock(Notifier.class);
        handler = new WaitlistInvitationDatesGoneHandler(notifier);
    }

    @Test
    void itHandlesItsOwnEvent() {
        assertEquals(WaitlistInvitationDatesGone.class, handler.eventType());
    }

    @Test
    void whoeverEditsTheListsIsToldWhoseInvitationHasNoDateLeft() {
        handler.handle(new WaitlistInvitationDatesGone(7, "Max Müller", "Schnupperstunde"));

        var captor = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier)
                .notify(
                        eq(StationAudience.holders(7, StationPermission.WAITLIST_EDIT)),
                        eq(NotificationType.WAITLIST_INVITATION_DATES_GONE),
                        captor.capture(),
                        eq(Delivery.EVERY_TIME));

        var params = (NotificationParams.WaitlistInvitationDatesGone)
                captor.getValue().params();
        assertEquals("Max Müller", params.childName());
        assertEquals("Schnupperstunde", params.listName());
        assertEquals("waiting-lists", captor.getValue().link().route());
    }
}
