/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.MessagePage;
import dev.chojo.ember.feature.messages.entity.MessageView;
import dev.chojo.ember.feature.messages.entity.Participation;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Reading and writing the messages of a conversation: a page of them, one of them, writing,
 * changing and deleting one's own.
 *
 * <p>Every change is told to the people in the conversation as a signal naming the message, which
 * their pages then fetch through {@link #one}.
 */
@Singleton
public class MessageService {
    /** How many messages one page holds. */
    public static final int PAGE_SIZE = 50;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageGuards guards;
    private final MessageViews views;
    private final MessageSignals signals;

    @Inject
    public MessageService(
            ConversationRepository conversations,
            MessageRepository messages,
            MessageGuards guards,
            MessageViews views,
            MessageSignals signals) {
        this.conversations = conversations;
        this.messages = messages;
        this.guards = guards;
        this.views = views;
        this.signals = signals;
    }

    /**
     * A page of a conversation: the newest messages, those before one message, or those after it.
     *
     * @param accountId       the reader
     * @param conversationUid the conversation
     * @param beforeId        read back from this message, or null
     * @param afterId         read on from this message, or null
     * @return the messages, oldest first
     */
    public MessagePage page(
            int accountId, UUID conversationUid, @Nullable Integer beforeId, @Nullable Integer afterId) {
        if (beforeId != null && afterId != null) throw MessageRefusal.PAGE_BOTH_WAYS.raise();
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        int conversationId = participation.conversation().id();
        List<Message> found = afterId != null
                ? messages.after(conversationId, afterId, PAGE_SIZE + 1)
                : messages.before(conversationId, beforeId, PAGE_SIZE + 1);
        return new MessagePage(views.of(trimmed(found, afterId != null), accountId), found.size() > PAGE_SIZE);
    }

    /**
     * A page cut to its size. One message more than a page is read to know whether there are
     * more, and it is the one furthest from where reading started that is dropped.
     */
    private static List<Message> trimmed(List<Message> found, boolean forward) {
        if (found.size() <= PAGE_SIZE) return found;
        return forward ? found.subList(0, PAGE_SIZE) : found.subList(1, found.size());
    }

    /**
     * One message of a conversation.
     *
     * @param accountId       the reader
     * @param conversationUid the conversation
     * @param messageId       the message
     * @return the message as the reader sees it
     */
    public MessageView one(int accountId, UUID conversationUid, int messageId) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        return views.of(guards.requireMessage(participation, messageId), accountId);
    }

    /**
     * Writes a message. A direct conversation either side had put away comes back to both, and the
     * writer has read everything up to their own message.
     *
     * @param accountId       the writer
     * @param conversationUid the conversation
     * @param content         the text
     * @param replyToId       the message it answers, or null
     * @return the message as the writer sees it
     */
    public MessageView send(int accountId, UUID conversationUid, String content, @Nullable Integer replyToId) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        String text = MessageTexts.content(content);
        if (replyToId != null) {
            messages.find(replyToId)
                    .filter(answered -> answered.conversationId()
                            == participation.conversation().id())
                    .orElseThrow(MessageRefusal.REPLY_NOT_HERE::raise);
        }
        guards.requireDeliverable(participation);
        int conversationId = participation.conversation().id();
        int id = Transactions.call(() -> {
            int written = messages.write(conversationId, accountId, text, replyToId);
            conversations.touch(conversationId);
            if (!participation.conversation().isGroup()) conversations.returnAll(conversationId);
            conversations.markRead(conversationId, accountId, written);
            return written;
        });
        tell(participation, id);
        return one(accountId, conversationUid, id);
    }

    /**
     * Changes the text of one's own message, which then shows as edited.
     *
     * @param accountId       the author
     * @param conversationUid the conversation
     * @param messageId       the message
     * @param content         the new text
     * @return the message as the author sees it
     */
    public MessageView edit(int accountId, UUID conversationUid, int messageId, String content) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Message message = guards.requireMessage(participation, messageId);
        if (!message.writtenBy(accountId)) throw MessageRefusal.EDIT_NOT_YOURS.raise();
        if (message.deleted()) throw MessageRefusal.EDIT_DELETED.raise();
        messages.edit(messageId, MessageTexts.content(content));
        tell(participation, messageId);
        return one(accountId, conversationUid, messageId);
    }

    /**
     * Deletes one's own message. Its place stays, shown as a deleted message.
     *
     * @param accountId       the author
     * @param conversationUid the conversation
     * @param messageId       the message
     */
    public void delete(int accountId, UUID conversationUid, int messageId) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Message message = guards.requireMessage(participation, messageId);
        if (!message.writtenBy(accountId)) throw MessageRefusal.DELETE_NOT_YOURS.raise();
        messages.delete(messageId);
        tell(participation, messageId);
    }

    private void tell(Participation participation, int messageId) {
        var conversation = participation.conversation();
        signals.publishTo(conversation.id(), MessageEvent.message(conversation.uid(), messageId), List.of());
    }
}
