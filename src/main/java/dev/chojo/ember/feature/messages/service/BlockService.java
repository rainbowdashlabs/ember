/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.repository.AccountBlockRepository;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocking people for messages.
 *
 * <p>A blocked person can no longer start a direct conversation with the blocker, add them to a
 * group or write to them directly. In a group they share, their messages are folded away for the
 * blocker and their reactions are not shown. They are never told.
 */
@Singleton
public class BlockService {
    private final AccountBlockRepository blocks;
    private final MessagePeopleRepository people;
    private final MessageGuards guards;

    @Inject
    public BlockService(AccountBlockRepository blocks, MessagePeopleRepository people, MessageGuards guards) {
        this.blocks = blocks;
        this.people = people;
        this.guards = guards;
    }

    /**
     * The people the reader blocked.
     *
     * @param accountId the reader
     * @return the blocked people, by name
     */
    public List<Person> blocked(int accountId) {
        guards.requireUsable(accountId);
        return blocks.blockedBy(accountId);
    }

    /**
     * Blocks a person.
     *
     * @param accountId the reader
     * @param otherUid  the person to block, or null when the request named nobody
     */
    public void block(int accountId, @Nullable UUID otherUid) {
        guards.requireUsable(accountId);
        Person other = Optional.ofNullable(otherUid)
                .flatMap(people::findByUid)
                .orElseThrow(MessageRefusal.BLOCK_PERSON_NOT_HERE::raise);
        if (other.accountId() == accountId) throw MessageRefusal.BLOCK_SELF.raise();
        blocks.block(accountId, other.accountId());
    }

    /**
     * Takes a block back.
     *
     * @param accountId the reader
     * @param otherUid  the person blocked
     */
    public void unblock(int accountId, UUID otherUid) {
        guards.requireUsable(accountId);
        people.findByUid(otherUid).ifPresent(other -> blocks.unblock(accountId, other.accountId()));
    }
}
