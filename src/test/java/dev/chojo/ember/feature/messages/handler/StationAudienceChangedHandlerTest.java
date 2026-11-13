/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.handler;

import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.messages.service.StationChatMembership;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** A change to a station's people plans a reconciliation of the chats it concerns. */
class StationAudienceChangedHandlerTest {
    private final StationChatMembership membership = mock(StationChatMembership.class);
    private final StationAudienceChangedHandler handler = new StationAudienceChangedHandler(membership);

    @Test
    void aStationsChangeReconcilesThatStation() {
        handler.handle(StationAudienceChanged.at(7));

        verify(membership).soon(7);
        verify(membership, never()).soonForAccount(anyInt());
    }

    @Test
    void anAccountsChangeReconcilesItsStations() {
        handler.handle(StationAudienceChanged.ofAccount(3));

        verify(membership).soonForAccount(3);
        verify(membership, never()).soon(anyInt());
    }

    @Test
    void itHandlesTheAudienceEvent() {
        assertEquals(StationAudienceChanged.class, handler.eventType());
    }
}
