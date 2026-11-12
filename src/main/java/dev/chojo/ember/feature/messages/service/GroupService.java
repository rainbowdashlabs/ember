/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.Participation;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Groups: starting one, adding and removing people, renaming, and leaving.
 *
 * <p>Any member adds people they could start a conversation with themselves; only the owner removes
 * people and renames the group. When the owner leaves, the group passes to whoever has been in it
 * longest. Every change leaves a quiet system line in the group. Somebody added later reads the
 * history from before they joined.
 */
@Singleton
public class GroupService {
    /** How many people a group holds at most, its starter included. */
    public static final int GROUP_SIZE = 25;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageGuards guards;
    private final MessageSignals signals;

    @Inject
    public GroupService(
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
     * Starts a group with the reader as its owner.
     *
     * @param accountId the reader
     * @param others    the people to take in, each somebody the reader may write to
     * @param name      the group's name, or null to call it after its members
     * @return the group
     */
    public Conversation create(int accountId, Collection<UUID> others, @Nullable String name) {
        guards.requireUsable(accountId);
        String kept = MessageTexts.groupName(name);
        List<Person> people = reachable(accountId, others);
        people.removeIf(person -> person.accountId() == accountId);
        if (people.isEmpty()) throw MessageRefusal.GROUP_NEEDS_SOMEBODY.raise();
        if (people.size() + 1 > GROUP_SIZE) throw MessageRefusal.GROUP_TOO_LARGE.raise();
        Conversation group = Transactions.call(() -> {
            var created = conversations.createGroup(accountId, kept);
            conversations.join(created.id(), accountId);
            people.forEach(person -> conversations.join(created.id(), person.accountId()));
            return created;
        });
        tellEverybody(group, List.of());
        return group;
    }

    /**
     * Adds people to a group. Somebody already in it is passed over.
     *
     * @param accountId       the member adding them
     * @param conversationUid the group
     * @param others          the people to add, each somebody the member may write to
     */
    public void add(int accountId, UUID conversationUid, Collection<UUID> others) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Conversation group = participation.conversation();
        if (!group.isGroup()) throw MessageRefusal.ADD_NOT_A_GROUP.raise();
        Set<UUID> present = new LinkedHashSet<>();
        conversations
                .activeMembers(group.id())
                .forEach(member -> present.add(member.person().uid()));
        List<Person> people = reachable(
                accountId, others.stream().filter(uid -> !present.contains(uid)).toList());
        if (people.isEmpty()) return;
        if (present.size() + people.size() > GROUP_SIZE) throw MessageRefusal.GROUP_TOO_LARGE.raise();
        Transactions.run(() -> {
            for (Person person : people) {
                conversations.join(group.id(), person.accountId());
                messages.writeSystem(group.id(), accountId, SystemKind.ADDED, person.accountId(), "");
            }
            conversations.touch(group.id());
        });
        tellEverybody(group, List.of());
    }

    /**
     * Removes somebody from a group, which only its owner does.
     *
     * @param accountId       the owner
     * @param conversationUid the group
     * @param otherUid        the person to remove
     */
    public void remove(int accountId, UUID conversationUid, UUID otherUid) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Conversation group = participation.conversation();
        if (!group.isGroup()) throw MessageRefusal.REMOVE_NOT_A_GROUP.raise();
        if (!Objects.equals(group.ownerAccountId(), accountId)) throw MessageRefusal.REMOVE_NOT_OWNER.raise();
        Membership removed = conversations.activeMembers(group.id()).stream()
                .filter(member -> member.person().uid().equals(otherUid))
                .findFirst()
                .orElseThrow(MessageRefusal.REMOVE_NOT_A_MEMBER::raise);
        if (removed.accountId() == accountId) throw MessageRefusal.REMOVE_SELF.raise();
        Transactions.run(() -> {
            conversations.leave(group.id(), removed.accountId());
            messages.writeSystem(group.id(), accountId, SystemKind.REMOVED, removed.accountId(), "");
            conversations.touch(group.id());
        });
        tellEverybody(group, List.of(removed.accountId()));
    }

    /**
     * Renames a group, which only its owner does.
     *
     * @param accountId       the owner
     * @param conversationUid the group
     * @param name            the new name, or blank to call it after its members
     */
    public void rename(int accountId, UUID conversationUid, @Nullable String name) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Conversation group = participation.conversation();
        if (!group.isGroup()) throw MessageRefusal.RENAME_NOT_A_GROUP.raise();
        if (!Objects.equals(group.ownerAccountId(), accountId)) throw MessageRefusal.RENAME_NOT_OWNER.raise();
        String kept = MessageTexts.groupName(name);
        if (Objects.equals(kept, group.name())) return;
        Transactions.run(() -> {
            conversations.rename(group.id(), kept);
            messages.writeSystem(group.id(), accountId, SystemKind.RENAMED, null, kept == null ? "" : kept);
            conversations.touch(group.id());
        });
        tellEverybody(group, List.of());
    }

    /**
     * Leaves a conversation. A group goes on without the reader, passing to its longest member if
     * the reader owned it; a direct conversation is only put away, and comes back with its next
     * message.
     *
     * @param accountId       the reader
     * @param conversationUid the conversation
     */
    public void leave(int accountId, UUID conversationUid) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Conversation conversation = participation.conversation();
        if (!conversation.isGroup()) {
            conversations.leave(conversation.id(), accountId);
            signals.publish(List.of(accountId), MessageEvent.conversation(conversation.uid()));
            return;
        }
        Transactions.run(() -> {
            conversations.leave(conversation.id(), accountId);
            messages.writeSystem(conversation.id(), accountId, SystemKind.LEFT, null, "");
            if (Objects.equals(conversation.ownerAccountId(), accountId)) passOwnership(conversation);
            conversations.touch(conversation.id());
        });
        tellEverybody(conversation, List.of(accountId));
    }

    private void passOwnership(Conversation group) {
        var heir = conversations.activeMembers(group.id()).stream().findFirst();
        conversations.setOwner(group.id(), heir.map(Membership::accountId).orElse(null));
        heir.ifPresent(
                member -> messages.writeSystem(group.id(), null, SystemKind.OWNER_PASSED, member.accountId(), ""));
    }

    private List<Person> reachable(int accountId, Collection<UUID> uids) {
        List<Person> people = new ArrayList<>();
        for (UUID uid : new LinkedHashSet<>(uids)) {
            Person person = guards.requirePerson(uid);
            if (person.accountId() != accountId) guards.requireReachable(accountId, person);
            people.add(person);
        }
        return people;
    }

    private void tellEverybody(Conversation conversation, Collection<Integer> alsoTell) {
        signals.publishTo(conversation.id(), MessageEvent.conversation(conversation.uid()), alsoTell);
    }
}
