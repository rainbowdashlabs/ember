/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.service.MessageSignals;
import io.javalin.http.sse.SseClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An opened stream is held open, told it is ready, hears its account's signals, and leaves the hub
 * when the tab closes.
 */
class MessageEventRoutesTest {
    private final MessageSignals signals = new MessageSignals(new ConversationRepository());
    private final MessageEventRoutes routes = new MessageEventRoutes(signals);

    @Test
    void anOpenedStreamIsHeldToldItIsReadyAndHearsItsSignals() {
        SseClient client = mock(SseClient.class);
        routes.attach(7, client);

        verify(client).keepAlive();
        verify(client).sendEvent("ready", "ok");

        var event = MessageEvent.message(UUID.randomUUID(), 3);
        signals.publish(List.of(7), event);
        verify(client).sendEvent(MessageSignals.SIGNAL, event);
        assertEquals(1, signals.openFor(7));
    }

    @Test
    void aClosedTabLeavesTheHub() {
        SseClient client = mock(SseClient.class);
        ArgumentCaptor<Runnable> onClose = ArgumentCaptor.forClass(Runnable.class);
        routes.attach(8, client);
        verify(client).onClose(onClose.capture());

        onClose.getValue().run();

        assertEquals(0, signals.openFor(8));
    }

    @Test
    void aTerminatedClientIsNotWrittenTo() {
        SseClient client = mock(SseClient.class);
        when(client.terminated()).thenReturn(true);

        assertFalse(new SseMessageStream(client).send("ping", "ok"));
        verify(client, never()).sendEvent(eq("ping"), any());
    }
}
