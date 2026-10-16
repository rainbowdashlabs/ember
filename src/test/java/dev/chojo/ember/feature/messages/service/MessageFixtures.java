/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.repository.AccountBlockRepository;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.ReactionRepository;

/**
 * The message services on the test database's real repositories, wired the way the application
 * wires them, with one hub of signals shared between them so a test can watch what was told.
 *
 * @param signals       the hub every service tells
 * @param conversations the conversation list, opening, details, muting and reading
 * @param groups        groups and their people
 * @param messages      reading and writing messages
 * @param reactions     reactions
 * @param blocks        blocking
 * @param settings      status, the mail setting and finding people
 * @param guards        the access decisions
 */
public record MessageFixtures(
        MessageSignals signals,
        ConversationService conversations,
        GroupService groups,
        MessageService messages,
        ReactionService reactions,
        BlockService blocks,
        MessageSettingsService settings,
        MessageGuards guards) {

    /**
     * Every message service, freshly built.
     *
     * @return the services
     */
    public static MessageFixtures build() {
        var conversationRepo = new ConversationRepository();
        var messageRepo = new MessageRepository();
        var peopleRepo = new MessagePeopleRepository();
        var blockRepo = new AccountBlockRepository();
        var guards = new MessageGuards(peopleRepo, conversationRepo, messageRepo, blockRepo);
        var signals = new MessageSignals(conversationRepo);
        var views = new MessageViews(messageRepo, new ReactionRepository());
        return new MessageFixtures(
                signals,
                new ConversationService(conversationRepo, messageRepo, guards, signals),
                new GroupService(conversationRepo, messageRepo, guards, signals),
                new MessageService(conversationRepo, messageRepo, guards, views, signals),
                new ReactionService(new ReactionRepository(), guards, views, signals),
                new BlockService(blockRepo, peopleRepo, guards),
                new MessageSettingsService(peopleRepo, conversationRepo, guards),
                guards);
    }

    /**
     * The settings service alone, for a test outside messages that only needs the guardian switch.
     *
     * @return the service
     */
    public static MessageSettingsService settingsAlone() {
        return build().settings();
    }
}
