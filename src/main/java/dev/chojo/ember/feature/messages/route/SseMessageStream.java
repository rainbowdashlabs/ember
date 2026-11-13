/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.feature.messages.service.MessageStream;
import io.javalin.http.sse.SseClient;

/**
 * A message stream written as server-sent events. Sends are one at a time, because the heartbeat
 * and a signal can arrive together and two events written into each other are neither.
 */
final class SseMessageStream implements MessageStream {
    private final SseClient client;

    SseMessageStream(SseClient client) {
        this.client = client;
    }

    @Override
    public synchronized boolean send(String event, Object data) {
        if (client.terminated()) return false;
        client.sendEvent(event, data);
        return !client.terminated();
    }
}
