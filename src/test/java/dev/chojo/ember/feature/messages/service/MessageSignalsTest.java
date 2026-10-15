/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.lifecycle.ScheduledTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The hub of open streams: a stream hears only its own account's signals, is pinged every 20
 * seconds, and is dropped once a send to it fails.
 */
class MessageSignalsTest {
    private final MessageSignals signals = new MessageSignals(new ConversationRepository());

    @Test
    void aStreamHearsOnlyItsOwnAccount() {
        var mine = new MessageTestBase.RecordingStream();
        var theirs = new MessageTestBase.RecordingStream();
        signals.join(1, mine);
        signals.join(2, theirs);

        signals.publish(List.of(1, 1), MessageEvent.read(UUID.randomUUID()));

        assertEquals(List.of("ready", MessageSignals.SIGNAL), mine.events);
        assertEquals(List.of("ready"), theirs.events);
    }

    @Test
    void aClosedStreamIsDroppedAtTheNextSend() {
        var stream = new MessageTestBase.RecordingStream();
        signals.join(1, stream);
        stream.open = false;

        signals.heartbeat();

        assertEquals(0, signals.openFor(1));
    }

    @Test
    void aStreamThatLeftHearsNothingMore() {
        var stream = new MessageTestBase.RecordingStream();
        signals.join(1, stream);
        signals.leave(1, stream);
        signals.leave(1, stream);

        signals.publish(List.of(1), MessageEvent.conversation(UUID.randomUUID()));

        assertEquals(List.of("ready"), stream.events);
    }

    @Test
    void everyOpenStreamIsPingedEveryTwentySeconds() {
        var stream = new MessageTestBase.RecordingStream();
        signals.join(1, stream);
        ScheduledTask task = signals.scheduledTasks().getFirst();

        task.work().run();

        assertEquals("message-stream-heartbeat", task.name());
        assertEquals(Duration.ofSeconds(20), task.schedule().period());
        assertEquals(List.of("ready", "ping"), stream.events);
    }
}
