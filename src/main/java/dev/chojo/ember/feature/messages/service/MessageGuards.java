/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.Participation;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.repository.AccountBlockRepository;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
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

    @Inject
    public MessageGuards(
            MessagePeopleRepository people,
            ConversationRepository conversations,
            MessageRepository messages,
            AccountBlockRepository blocks) {
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
     * conversation that does not exist and one that is somebody else's answer alike.
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
     * Refuses a message to a direct conversation whose other side cannot take it: the account is
     * gone, has no messages any more, or blocked the writer. The refusal does not say which, so a
     * blocked writer is not told. A group takes every message; blocking there only hides.
     *
     * @param participation the writer in the conversation
     */
    public void requireDeliverable(Participation participation) {
        if (participation.conversation().isGroup()) return;
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
