/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.entity.MessagesStatus;
import dev.chojo.ember.feature.messages.entity.ReachablePerson;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * What an account allows about its messages, and whom it can write to.
 *
 * <p>The guardian switch lives here too: for an account with a guardian at any station, messages
 * are off until a guardian turns them on. Turning them off again hides the conversations from the
 * account and stops new messages; nothing is deleted.
 */
@Singleton
public class MessageSettingsService {
    /** How many people one search for somebody to write to answers at most. */
    public static final int SEARCH_LIMIT = 20;

    private final MessagePeopleRepository people;
    private final ConversationRepository conversations;
    private final MessageGuards guards;

    @Inject
    public MessageSettingsService(
            MessagePeopleRepository people, ConversationRepository conversations, MessageGuards guards) {
        this.people = people;
        this.conversations = conversations;
        this.guards = guards;
    }

    /**
     * Whether the account can use messages, how many wait unread, and whether it wants mail.
     *
     * @param accountId the account
     * @return the status, with nothing unread for an account that cannot use messages
     */
    public MessagesStatus status(int accountId) {
        boolean available = people.usable(accountId);
        int unread = available ? conversations.unreadTotal(accountId) : 0;
        return new MessagesStatus(available, unread, people.mailWanted(accountId));
    }

    /**
     * Turns the mail about unread messages on or off.
     *
     * @param accountId the account
     * @param wanted    whether it wants the mail
     * @return the status after the change
     */
    public MessagesStatus setMail(int accountId, boolean wanted) {
        guards.requireUsable(accountId);
        people.setMailWanted(accountId, wanted);
        return status(accountId);
    }

    /**
     * The people the account may write to whose name holds the search, each with one station they share.
     *
     * @param accountId the account
     * @param search    part of a name, or blank for everybody
     * @return the people found, by name
     */
    public List<ReachablePerson> reachable(int accountId, String search) {
        guards.requireUsable(accountId);
        return people.searchReachable(accountId, search, SEARCH_LIMIT);
    }

    /**
     * Whether a guardian turned messages on for an account.
     *
     * @param accountId the account a guardian looks after
     * @return the switch
     */
    public boolean guardianAllowed(int accountId) {
        return people.guardianAllowed(accountId);
    }

    /**
     * Turns messages on or off for an account a guardian looks after. Who may do so is decided by
     * the caller, which checks the guardian.
     *
     * @param accountId the account
     * @param allowed   whether it may use messages
     */
    public void setGuardianAllowed(int accountId, boolean allowed) {
        people.setGuardianAllowed(accountId, allowed);
    }
}
