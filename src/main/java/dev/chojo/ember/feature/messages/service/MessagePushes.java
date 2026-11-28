/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.MessagePush;
import dev.chojo.ember.feature.messages.entity.StationRef;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.feature.push.service.PushService;
import dev.chojo.ember.feature.system.service.InstanceSettingsService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * New messages as pushes: who gets one, and what it shows each of them.
 *
 * <p>Everybody taking part gets one for whom the message counts as unread and who can use messages,
 * so not the writer, nobody who muted the conversation or blocked the writer, and no ward whose
 * guardian has not allowed messages; and not whoever has Ember open right now, since an open window
 * already shows the message. That last one is known
 * only to the node holding the reader's stream, so the pushes are queued right after the message
 * rather than in the statement that writes it. What a push shows is written when it goes out: the
 * conversation by the name this reader knows it under, the writer and the text, and nothing once any
 * of those no longer holds, the reader read it, or the instance switched messages off. A failure to
 * queue costs only the pushes: the message was written and is not sent again.
 */
@Singleton
public class MessagePushes {
    private static final Logger log = getLogger(MessagePushes.class);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final StationChatRepository stationChats;
    private final MessageSignals signals;
    private final PushService push;
    private final InstanceSettingsService instance;
    private final Api api;

    @Inject
    public MessagePushes(
            ConversationRepository conversations,
            MessageRepository messages,
            StationChatRepository stationChats,
            MessageSignals signals,
            PushService push,
            InstanceSettingsService instance,
            Api api) {
        this.conversations = conversations;
        this.messages = messages;
        this.stationChats = stationChats;
        this.signals = signals;
        this.push = push;
        this.instance = instance;
        this.api = api;
    }

    /**
     * Queues a new message as a push to the devices of everybody who should get one. Never throws,
     * since the message is written already and a writer given a failure would send it twice.
     *
     * @param conversation the conversation
     * @param authorId     the writer, who gets none
     * @param messageId    the message
     */
    public void queue(Conversation conversation, int authorId, int messageId) {
        try {
            var readers = conversations.pushReaders(messageId).stream()
                    .filter(reader -> reader != authorId)
                    .filter(reader -> signals.openFor(reader) == 0)
                    .toList();
            push.queueMessage(messageId, readers);
        } catch (RuntimeException e) {
            log.warn("Pushes for message {} in conversation {} could not be queued", messageId, conversation.id(), e);
        }
    }

    /**
     * What a push about a message shows one reader.
     *
     * @param messageId the message
     * @param readerId  the reader's account
     * @return the push, empty when there is nothing left to show them
     */
    public Optional<MessagePush> forReader(int messageId, int readerId) {
        if (!instance.messagesEnabled()) return Optional.empty();
        var message = messages.find(messageId).filter(found -> found.written() && !found.deleted());
        if (message.isEmpty()) return Optional.empty();
        var conversation = conversations.findAll(List.of(message.get().conversationId())).stream()
                .findFirst();
        if (conversation.isEmpty()) return Optional.empty();
        if (!conversations.stillPushable(messageId, readerId)) return Optional.empty();
        var members = conversations.activeMembers(conversation.get().id());
        String title = ConversationTitles.titleOf(
                conversation.get(),
                readerId,
                stationsOf(conversation.get()),
                members,
                () -> conversations.everyMember(conversation.get().id()));
        String body = ConversationTitles.nameOf(message.get().author()) + ": "
                + MessageTexts.plain(message.get().content());
        String uid = conversation.get().uid().toString();
        return Optional.of(new MessagePush(title, body, api.baseUrl() + "/messages/" + uid, "conversation|" + uid));
    }

    private Map<Integer, StationRef> stationsOf(Conversation conversation) {
        StationScope scope = conversation.station();
        return scope == null ? Map.of() : stationChats.stations(Set.of(scope.stationId()));
    }
}
