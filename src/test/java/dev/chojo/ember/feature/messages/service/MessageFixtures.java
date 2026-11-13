/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.messages.repository.AccountBlockRepository;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import dev.chojo.ember.feature.messages.repository.MessageReportRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.ReactionRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.lifecycle.TaskScheduler;

import java.util.Set;

import static org.mockito.Mockito.mock;

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
 * @param membership    the reconciliation of station chats with their audience
 * @param stationChats  opening and handling station chats
 * @param moderation    removing messages for a station
 * @param reports       reporting messages and handling the reports
 * @param notifier      a mock that the reports tell the people handling a station's chats through
 */
public record MessageFixtures(
        MessageSignals signals,
        ConversationService conversations,
        GroupService groups,
        MessageService messages,
        ReactionService reactions,
        BlockService blocks,
        MessageSettingsService settings,
        MessageGuards guards,
        StationChatMembership membership,
        StationChatService stationChats,
        MessageModeration moderation,
        MessageReportService reports,
        Notifier notifier) {

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
        var stationChatRepo = new StationChatRepository();
        var guards = new MessageGuards(peopleRepo, conversationRepo, messageRepo, blockRepo, stationChatRepo);
        var signals = new MessageSignals(conversationRepo);
        var views = new MessageViews(messageRepo, new ReactionRepository(), stationChatRepo);
        var moderation = new MessageModeration(messageRepo, new MessageReportRepository(), stationChatRepo, signals);
        var notifier = mock(Notifier.class);
        var membership =
                new StationChatMembership(stationChatRepo, conversationRepo, messageRepo, new TaskScheduler(), signals);
        return new MessageFixtures(
                signals,
                new ConversationService(conversationRepo, messageRepo, guards, signals, stationChatRepo),
                new GroupService(conversationRepo, messageRepo, guards, signals),
                new MessageService(conversationRepo, messageRepo, guards, views, signals, moderation),
                new ReactionService(new ReactionRepository(), guards, views, signals),
                new BlockService(blockRepo, peopleRepo, guards),
                new MessageSettingsService(peopleRepo, conversationRepo, guards, new DomainEventBus(Set.of())),
                guards,
                membership,
                new StationChatService(stationChatRepo, conversationRepo, messageRepo, membership, signals),
                moderation,
                new MessageReportService(
                        new MessageReportRepository(),
                        messageRepo,
                        guards,
                        views,
                        moderation,
                        notifier,
                        stationChatRepo),
                notifier);
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
