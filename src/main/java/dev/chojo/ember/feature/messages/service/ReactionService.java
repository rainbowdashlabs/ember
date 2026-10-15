/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.MessageView;
import dev.chojo.ember.feature.messages.entity.Participation;
import dev.chojo.ember.feature.messages.repository.ReactionRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

/**
 * Reacting to messages with emoji, and taking a reaction back.
 *
 * <p>A reaction reaches the others live like a message, but it never counts as unread, never
 * shows on a badge and is never mailed about: it is not something anybody has to read.
 */
@Singleton
public class ReactionService {
    private final ReactionRepository reactions;
    private final MessageGuards guards;
    private final MessageViews views;
    private final MessageSignals signals;

    @Inject
    public ReactionService(
            ReactionRepository reactions, MessageGuards guards, MessageViews views, MessageSignals signals) {
        this.reactions = reactions;
        this.guards = guards;
        this.views = views;
        this.signals = signals;
    }

    /**
     * Reacts to a message. Reacting again with the same emoji changes nothing.
     *
     * @param accountId       the reader
     * @param conversationUid the conversation
     * @param messageId       the message
     * @param emoji           a standard emoji
     * @return the message as the reader sees it now
     */
    public MessageView react(int accountId, UUID conversationUid, int messageId, String emoji) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Message message = guards.requireMessage(participation, messageId);
        if (!message.written() || message.deleted()) throw MessageRefusal.REACTION_NOT_TAKEN.raise();
        if (!MessageEmoji.isStandard(emoji)) throw MessageRefusal.EMOJI_NOT_TAKEN.raise();
        if (reactions.add(messageId, accountId, emoji)) tell(participation, messageId);
        return views.of(message, accountId);
    }

    /**
     * Takes one's own reaction back. Taking back one that is not there changes nothing.
     *
     * @param accountId       the reader
     * @param conversationUid the conversation
     * @param messageId       the message
     * @param emoji           the emoji reacted with
     * @return the message as the reader sees it now
     */
    public MessageView takeBack(int accountId, UUID conversationUid, int messageId, String emoji) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Message message = guards.requireMessage(participation, messageId);
        if (reactions.remove(messageId, accountId, emoji)) tell(participation, messageId);
        return views.of(message, accountId);
    }

    private void tell(Participation participation, int messageId) {
        var conversation = participation.conversation();
        signals.publishTo(conversation.id(), MessageEvent.message(conversation.uid(), messageId), List.of());
    }
}
