/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.events.WaitlistInvitationAnswered;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * An answer that only appears on a screen somebody has to visit is not an answer the station
 * receives, so whoever looks after the lists is told.
 */
class WaitlistInvitationAnsweredHandlerTest {

    private NotificationService notificationService;
    private WaitlistInvitationAnsweredHandler handler;

    @BeforeEach
    void setup() {
        notificationService = mock(NotificationService.class);
        handler = new WaitlistInvitationAnsweredHandler(notificationService);
    }

    @Test
    void itHandlesItsOwnEvent() {
        assertEquals(WaitlistInvitationAnswered.class, handler.eventType());
    }

    @Test
    void whoeverEditsTheListsIsToldWhoAnsweredAndWhat() {
        handler.handle(new WaitlistInvitationAnswered(7, "Max Müller", "Schnupperstunde", WaitingListAnswer.COMING));

        var captor = ArgumentCaptor.forClass(NotificationData.class);
        verify(notificationService)
                .notifyMembersWithRole(
                        eq(7),
                        eq("WAITLIST_EDIT"),
                        eq(NotificationType.WAITLIST_INVITATION_ANSWERED),
                        captor.capture());

        var params = (NotificationParams.WaitlistInvitationAnswered)
                captor.getValue().params();
        assertEquals("Max Müller", params.childName());
        assertEquals("Schnupperstunde", params.listName());
        assertEquals("COMING", params.answer(), "the name travels and the label is looked up per reader");
        assertEquals("waiting-lists", captor.getValue().link().route());
    }
}
