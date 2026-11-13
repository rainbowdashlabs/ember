/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageView;
import dev.chojo.ember.feature.messages.entity.Reaction;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.ReactionRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Puts together what a reader sees of messages: the message each answers and the reactions to it,
 * read in one go for a whole page. Who removed a message for the station is shown only to the people
 * handling the station's chats.
 */
@Singleton
public class MessageViews {
    private final MessageRepository messages;
    private final ReactionRepository reactions;
    private final StationChatRepository stationChats;

    @Inject
    public MessageViews(MessageRepository messages, ReactionRepository reactions, StationChatRepository stationChats) {
        this.messages = messages;
        this.reactions = reactions;
        this.stationChats = stationChats;
    }

    /**
     * The messages as the reader sees them, in the order given.
     *
     * @param page     the messages
     * @param readerId the reader
     * @return each with its answered message and its reactions
     */
    public List<MessageView> of(List<Message> shown, int readerId) {
        List<Message> page = seesRemovers(shown, readerId)
                ? shown
                : shown.stream().map(Message::withoutRemover).toList();
        Map<Integer, Message> known = page.stream().collect(Collectors.toMap(Message::id, Function.identity()));
        List<Integer> missing = page.stream()
                .map(Message::replyToId)
                .filter(Objects::nonNull)
                .filter(id -> !known.containsKey(id))
                .distinct()
                .toList();
        Map<Integer, Message> answered = new HashMap<>(known);
        messages.findAll(missing).forEach(message -> answered.put(message.id(), message));
        Map<Integer, List<Reaction>> reactionsOf = reactions.forMessages(List.copyOf(known.keySet()), readerId).stream()
                .collect(Collectors.groupingBy(Reaction::messageId));
        return page.stream()
                .map(message -> new MessageView(
                        message, replyOf(message, answered), reactionsOf.getOrDefault(message.id(), List.of())))
                .toList();
    }

    /**
     * One message as the reader sees it.
     *
     * @param message  the message
     * @param readerId the reader
     * @return the message with its answered message and its reactions
     */
    public MessageView of(Message message, int readerId) {
        return of(List.of(message), readerId).getFirst();
    }

    private boolean seesRemovers(List<Message> page, int readerId) {
        return page.stream()
                .filter(message -> message.removedByStation() && message.deletedBy() != null)
                .findFirst()
                .flatMap(removed -> stationChats.stationOf(removed.conversationId()))
                .map(stationId -> stationChats.moderates(readerId, stationId))
                .orElse(true);
    }

    private static @Nullable Message replyOf(Message message, Map<Integer, Message> answered) {
        Integer replyToId = message.replyToId();
        return replyToId == null ? null : answered.get(replyToId);
    }
}
