/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open message streams of every signed-in tab, and the signals sent down them.
 *
 * <p>A signal names a conversation and what kind of news it has, never what was written: the page
 * fetches that through the normal routes, which check who may read it. A stream that cannot be
 * written to any more is dropped the moment a send fails.
 *
 * <p>Every open stream is sent a {@code ping} every 20 seconds. That keeps proxies from closing a
 * quiet connection, and lets the page notice a stream that went silent and fall back to asking on
 * its own.
 */
@Singleton
public class MessageSignals implements TaskSource {
    /** The event every signal is sent as. */
    public static final String SIGNAL = "signal";

    /**
     * What a ready or ping event carries. Browsers drop an event whose data is empty, so these carry
     * a word that means nothing beyond the event arriving.
     */
    private static final String ALIVE = "ok";

    private static final Duration HEARTBEAT = Duration.ofSeconds(20);

    private final Map<Integer, Set<MessageStream>> streams = new ConcurrentHashMap<>();
    private final ConversationRepository conversations;

    @Inject
    public MessageSignals(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    /**
     * Takes in a newly opened stream and tells it that it is ready, which is how the page knows the
     * stream gets through.
     *
     * @param accountId the account the tab is signed in as
     * @param stream    the stream
     */
    public void join(int accountId, MessageStream stream) {
        streams.computeIfAbsent(accountId, id -> ConcurrentHashMap.newKeySet()).add(stream);
        send(accountId, stream, "ready", ALIVE);
    }

    /**
     * Drops a stream whose tab closed.
     *
     * @param accountId the account the tab was signed in as
     * @param stream    the stream
     */
    public void leave(int accountId, MessageStream stream) {
        streams.computeIfPresent(accountId, (id, open) -> {
            open.remove(stream);
            return open.isEmpty() ? null : open;
        });
    }

    /**
     * Sends a signal to every open stream of the given accounts.
     *
     * @param accountIds the accounts to tell
     * @param event      the signal
     */
    public void publish(Collection<Integer> accountIds, MessageEvent event) {
        for (int accountId : Set.copyOf(accountIds)) {
            for (MessageStream stream : List.copyOf(streams.getOrDefault(accountId, Set.of()))) {
                send(accountId, stream, SIGNAL, event);
            }
        }
    }

    /**
     * Sends a signal to everybody taking part in a conversation right now, and to anybody else named.
     *
     * @param conversationId the conversation
     * @param event          the signal
     * @param alsoTell       accounts to tell besides, such as somebody who just left
     */
    public void publishTo(int conversationId, MessageEvent event, Collection<Integer> alsoTell) {
        List<Integer> told = new ArrayList<>(alsoTell);
        conversations.activeMembers(conversationId).forEach(member -> told.add(member.accountId()));
        publish(told, event);
    }

    /**
     * How many streams are open for an account.
     *
     * @param accountId the account
     * @return the number of its open streams
     */
    public int openFor(int accountId) {
        return streams.getOrDefault(accountId, Set.of()).size();
    }

    /** Pings every open stream, dropping those that are gone. */
    void heartbeat() {
        for (var entry : Map.copyOf(streams).entrySet()) {
            for (MessageStream stream : List.copyOf(entry.getValue())) {
                send(entry.getKey(), stream, "ping", ALIVE);
            }
        }
    }

    private void send(int accountId, MessageStream stream, String event, Object data) {
        if (!stream.send(event, data)) leave(accountId, stream);
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "message-stream-heartbeat", Schedule.fixedRate(HEARTBEAT, HEARTBEAT), this::heartbeat));
    }
}
