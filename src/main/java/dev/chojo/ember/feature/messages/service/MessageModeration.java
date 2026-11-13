/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.ReportOutcome;
import dev.chojo.ember.feature.messages.repository.MessageReportRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * What the people handling a station's chats do to a message: remove it for the station.
 *
 * <p>A removed message shows as removed by the station to everybody; who removed it shows only to
 * the people handling the station's chats. Its author is not told on their own, the line in the chat
 * says it. Every open report of the message is handled with the removal.
 */
@Singleton
public class MessageModeration {
    private static final Logger log = LoggerFactory.getLogger(MessageModeration.class);

    private final MessageRepository messages;
    private final MessageReportRepository reports;
    private final StationChatRepository stationChats;
    private final MessageSignals signals;

    @Inject
    public MessageModeration(
            MessageRepository messages,
            MessageReportRepository reports,
            StationChatRepository stationChats,
            MessageSignals signals) {
        this.messages = messages;
        this.reports = reports;
        this.stationChats = stationChats;
        this.signals = signals;
    }

    /**
     * Whether an account handles the chats of the station a conversation belongs to.
     *
     * @param accountId    the account
     * @param conversation the conversation
     * @return false for a conversation that is no station chat
     */
    public boolean moderates(int accountId, Conversation conversation) {
        var scope = conversation.station();
        return scope != null && stationChats.moderates(accountId, scope.stationId());
    }

    /**
     * Deletes the author's own message. Open reports of it are handled with it, the author named as
     * who did, since there is nothing left to decide about.
     *
     * @param accountId the author
     * @param messageId the message
     */
    public void deleteOwn(int accountId, int messageId) {
        Transactions.run(() -> {
            messages.delete(messageId, accountId);
            reports.handleAll(messageId, accountId, ReportOutcome.DELETED);
        });
    }

    /**
     * Removes a message for the station and handles its open reports as removed.
     *
     * @param accountId    the account removing it, whose right to do so the caller checked
     * @param conversation the station chat the message is in
     * @param messageId    the message
     */
    public void remove(int accountId, Conversation conversation, int messageId) {
        boolean removed = Transactions.call(() -> {
            boolean changed = messages.removeForStation(messageId, accountId);
            reports.handleAll(messageId, accountId, ReportOutcome.DELETED);
            return changed;
        });
        if (!removed) return;
        log.info("Message {} in station chat {} removed by account {}", messageId, conversation.id(), accountId);
        signals.publishTo(conversation.id(), MessageEvent.message(conversation.uid(), messageId), List.of());
    }
}
