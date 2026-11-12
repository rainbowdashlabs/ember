/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What an account's deletion does to messages.
 *
 * <p>Its memberships go, its messages stay for the others with the author shown as a deleted
 * account, and a conversation nobody is left in goes entirely. Blocks and reactions go with the
 * account by their foreign keys, and the author is cleared the same way.
 */
@Singleton
public class MessageErasure {
    private final ConversationRepository conversations;

    @Inject
    public MessageErasure(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    /**
     * Takes an account out of messages before the account itself is deleted.
     *
     * @param accountId the account being deleted
     */
    public void forget(int accountId) {
        conversations.forgetAccount(accountId);
    }
}
