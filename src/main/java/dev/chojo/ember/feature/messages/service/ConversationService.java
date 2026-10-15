/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.ConversationDetail;
import dev.chojo.ember.feature.messages.entity.ConversationOverview;
import dev.chojo.ember.feature.messages.entity.ConversationSummary;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.Participation;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The reader's conversations: the list, opening a direct one, one conversation's details, muting
 * and reading. Everything that changes a group's people or name is {@link GroupService}'s.
 */
@Singleton
public class ConversationService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageGuards guards;
    private final MessageSignals signals;

    @Inject
    public ConversationService(
            ConversationRepository conversations,
            MessageRepository messages,
            MessageGuards guards,
            MessageSignals signals) {
        this.conversations = conversations;
        this.messages = messages;
        this.guards = guards;
        this.signals = signals;
    }

    /**
     * The conversations an account takes part in, the most recently active first.
     *
     * @param accountId the reader
     * @return each with the other people in it and its newest message
     */
    public List<ConversationSummary> list(int accountId) {
        guards.requireUsable(accountId);
        List<ConversationOverview> overviews = conversations.overviews(accountId);
        List<Integer> ids = overviews.stream().map(o -> o.conversation().id()).toList();
        Map<Integer, List<Person>> others = ids.isEmpty()
                ? Map.of()
                : conversations.activeMembers(ids).stream()
                        .filter(member -> member.accountId() != accountId)
                        .collect(Collectors.groupingBy(
                                Membership::conversationId,
                                Collectors.mapping(Membership::person, Collectors.toList())));
        Map<Integer, Message> newest =
                messages.newestOf(ids).stream().collect(Collectors.toMap(Message::conversationId, Function.identity()));
        return overviews.stream()
                .map(overview -> new ConversationSummary(
                        overview,
                        others.getOrDefault(overview.conversation().id(), List.of()),
                        newest.get(overview.conversation().id())))
                .toList();
    }

    /**
     * The direct conversation between the reader and somebody, started where there is none yet.
     *
     * <p>A conversation that exists already is opened whatever has changed since: it stays even
     * when the two share no station any more. Only a new one needs the reader to reach the person.
     * The other side sees a new conversation once something is written in it.
     *
     * @param accountId the reader
     * @param otherUid  the other person
     * @return the conversation
     */
    public Conversation openDirect(int accountId, UUID otherUid) {
        guards.requireUsable(accountId);
        Person other = guards.requirePerson(otherUid);
        if (conversations.findDirect(accountId, other.accountId()).isEmpty()) {
            guards.requireReachable(accountId, other);
        }
        Conversation conversation = Transactions.call(() -> {
            var direct = conversations.directOf(accountId, other.accountId());
            conversations.join(direct.id(), accountId);
            conversations.include(direct.id(), other.accountId());
            return direct;
        });
        signals.publish(List.of(accountId), MessageEvent.conversation(conversation.uid()));
        return conversation;
    }

    /**
     * A conversation the reader takes part in, with everybody in it.
     *
     * @param accountId       the reader
     * @param conversationUid the conversation
     * @return the details
     */
    public ConversationDetail detail(int accountId, UUID conversationUid) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        return new ConversationDetail(
                participation.conversation(),
                conversations.activeMembers(participation.conversation().id()),
                participation.membership());
    }

    /**
     * Mutes or unmutes a conversation for the reader: no badge count and no mail while muted.
     *
     * @param accountId       the reader
     * @param conversationUid the conversation
     * @param muted           whether to mute it
     */
    public void mute(int accountId, UUID conversationUid, boolean muted) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        conversations.setMuted(participation.conversation().id(), accountId, muted);
        signals.publish(List.of(accountId), MessageEvent.read(conversationUid));
    }

    /**
     * Notes that the reader has read a conversation up to a message. Reading never moves back.
     *
     * @param accountId       the reader
     * @param conversationUid the conversation
     * @param messageId       the message read up to
     */
    public void read(int accountId, UUID conversationUid, int messageId) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        guards.requireMessage(participation, messageId);
        if (conversations.markRead(participation.conversation().id(), accountId, messageId)) {
            signals.publish(List.of(accountId), MessageEvent.read(conversationUid));
        }
    }

    /**
     * How many messages the account has not read, muted conversations left out.
     *
     * @param accountId the account
     * @return the count for its badge
     */
    public int unread(int accountId) {
        return conversations.unreadTotal(accountId);
    }
}
