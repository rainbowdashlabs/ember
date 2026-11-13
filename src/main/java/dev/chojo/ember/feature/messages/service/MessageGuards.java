/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.Participation;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.messages.repository.AccountBlockRepository;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * The access decisions of messages.
 *
 * <p>Messages belong to the people in a conversation and to no station, so every decision is made
 * from the reader's account alone: whether it may use messages at all, whether it takes part in the
 * conversation it names, and whether it may write to the person it names.
 */
@Singleton
public class MessageGuards {
    private final MessagePeopleRepository people;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AccountBlockRepository blocks;
    private final StationChatRepository stationChats;

    @Inject
    public MessageGuards(
            MessagePeopleRepository people,
            ConversationRepository conversations,
            MessageRepository messages,
            AccountBlockRepository blocks,
            StationChatRepository stationChats) {
        this.stationChats = stationChats;
        this.people = people;
        this.conversations = conversations;
        this.messages = messages;
        this.blocks = blocks;
    }

    /**
     * Refuses an account that cannot use messages, which is one with a guardian who has not turned
     * them on.
     *
     * @param accountId the account
     */
    public void requireUsable(int accountId) {
        if (!people.usable(accountId)) throw MessageRefusal.MESSAGES_NOT_TURNED_ON.raise();
    }

    /**
     * The conversation an account names, refused unless it takes part in it right now. A
     * conversation that does not exist and one that is somebody else's answer alike, and so does a
     * station chat whose station moved to another installation, where the chat lives on.
     *
     * @param accountId       the account
     * @param conversationUid the conversation
     * @return the conversation and the account's membership
     */
    public Participation requireParticipant(int accountId, UUID conversationUid) {
        requireUsable(accountId);
        var conversation =
                conversations.findByUid(conversationUid).orElseThrow(MessageRefusal.CONVERSATION_NOT_HERE::raise);
        Membership membership = conversations
                .membership(conversation.id(), accountId)
                .filter(Membership::active)
                .orElseThrow(MessageRefusal.CONVERSATION_NOT_HERE::raise);
        StationScope scope = conversation.station();
        if (scope != null && stationChats.movedAway(scope.stationId())) {
            throw MessageRefusal.CONVERSATION_NOT_HERE.raise();
        }
        return new Participation(conversation, membership);
    }

    /**
     * A message of the conversation the reader takes part in.
     *
     * @param participation the reader in the conversation
     * @param messageId     the message
     * @return the message, refused where it is in another conversation or nowhere
     */
    public Message requireMessage(Participation participation, int messageId) {
        return messages.find(messageId)
                .filter(message ->
                        message.conversationId() == participation.conversation().id())
                .orElseThrow(MessageRefusal.MESSAGE_NOT_HERE::raise);
    }

    /**
     * Refuses anything new in a closed station chat: messages, changes to them and reactions. A
     * closed chat stays readable, and one's own messages can still be deleted.
     *
     * @param participation the reader in the conversation
     */
    public void requireOpen(Participation participation) {
        StationScope scope = participation.conversation().station();
        if (scope != null && scope.closed()) throw MessageRefusal.CHAT_CLOSED.raise();
    }

    /**
     * Refuses any change to a station chat whose station is moving to another installation or has
     * moved there: what is written after the move read the chat would never reach its new home.
     * Direct conversations and groups stay here and are never refused.
     *
     * @param conversation the conversation
     */
    public void requireNotMoving(Conversation conversation) {
        StationScope scope = conversation.station();
        if (scope != null && stationChats.moving(scope.stationId())) {
            throw MessageRefusal.STATION_CHAT_MOVING.raise();
        }
    }

    /**
     * The same, asked again inside the transaction that writes, and holding a close back until that
     * transaction ends. Without it a message could land after a close that was committed between the
     * first look and the write.
     *
     * @param participation the reader in the conversation
     */
    public void requireStillOpen(Participation participation) {
        Conversation conversation = participation.conversation();
        if (conversation.isStation() && stationChats.closedNow(conversation.id())) {
            throw MessageRefusal.CHAT_CLOSED.raise();
        }
    }

    /**
     * The same for sending, which touches the chat's row afterwards: the row is locked the way that
     * touch locks it, so senders queue on it instead of waiting on each other.
     *
     * @param participation the writer in the conversation
     */
    public void requireStillOpenForSending(Participation participation) {
        Conversation conversation = participation.conversation();
        if (conversation.isStation() && stationChats.closedNowForSending(conversation.id())) {
            throw MessageRefusal.CHAT_CLOSED.raise();
        }
    }

    /**
     * Refuses a message to a direct conversation whose other side cannot take it: the account is
     * gone, has no messages any more, or blocked the writer. The refusal does not say which, so a
     * blocked writer is not told. A group or station chat takes every message; blocking there only
     * hides.
     *
     * @param participation the writer in the conversation
     */
    public void requireDeliverable(Participation participation) {
        if (!participation.conversation().isDirect()) return;
        int writerId = participation.accountId();
        boolean delivered = conversations
                .everyMember(participation.conversation().id())
                .stream()
                .map(Membership::accountId)
                .filter(id -> id != writerId)
                .anyMatch(other -> people.usable(other) && !blocks.blocks(other, writerId));
        if (!delivered) throw MessageRefusal.MESSAGE_NOT_DELIVERED.raise();
    }

    /**
     * The person a writer names, refused unless the writer may write to them: they share a current
     * station, the person can use messages and has not blocked the writer. Every reason answers
     * alike, so a blocked writer cannot tell.
     *
     * @param writerId  the account writing
     * @param targetUid the person named
     * @return the person
     */
    public Person requireReachable(int writerId, UUID targetUid) {
        return requireReachable(writerId, requirePerson(targetUid));
    }

    /**
     * The same, for a person already looked up.
     *
     * @param writerId the account writing
     * @param target   the person named
     * @return the person
     */
    public Person requireReachable(int writerId, Person target) {
        if (!people.reaches(writerId, target.accountId())) throw MessageRefusal.PERSON_NOT_REACHABLE.raise();
        return target;
    }

    /**
     * The account a writer names, refused with the same answer as one they cannot reach.
     *
     * @param uid the account's identity
     * @return the person
     */
    public Person requirePerson(UUID uid) {
        return people.findByUid(uid).orElseThrow(MessageRefusal.PERSON_NOT_REACHABLE::raise);
    }
}
