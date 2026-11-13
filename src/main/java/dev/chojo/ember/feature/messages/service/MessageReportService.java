/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageReport;
import dev.chojo.ember.feature.messages.entity.Participation;
import dev.chojo.ember.feature.messages.entity.ReportOutcome;
import dev.chojo.ember.feature.messages.entity.ReportedMessage;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.messages.repository.MessageReportRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Members reporting messages of a station chat, and the people handling the station's chats looking
 * at them.
 *
 * <p>Each new report tells the holders of the permission at the chat's station. A report is looked
 * at with the message in its context; the message is then removed for the station or the report is
 * dismissed. Either way every open report of that message is handled with it, since one decision
 * answers them all.
 */
@Singleton
public class MessageReportService {
    /** The longest a reason may be. */
    public static final int REASON_LENGTH = 500;

    /** How many messages before and after a reported one are shown with it. */
    static final int CONTEXT = 3;

    private static final Logger log = LoggerFactory.getLogger(MessageReportService.class);

    private final MessageReportRepository reports;
    private final MessageRepository messages;
    private final MessageGuards guards;
    private final MessageViews views;
    private final MessageModeration moderation;
    private final Notifier notifier;
    private final StationChatRepository stationChats;

    @Inject
    public MessageReportService(
            MessageReportRepository reports,
            MessageRepository messages,
            MessageGuards guards,
            MessageViews views,
            MessageModeration moderation,
            Notifier notifier,
            StationChatRepository stationChats) {
        this.stationChats = stationChats;
        this.reports = reports;
        this.messages = messages;
        this.guards = guards;
        this.views = views;
        this.moderation = moderation;
        this.notifier = notifier;
    }

    /**
     * Reports a message of a station chat the reader takes part in, keeping its text as it reads
     * now. Reporting it again while the first report is open changes nothing and tells nobody again,
     * and the reporter is not told about their own report.
     *
     * @param accountId       the reader
     * @param conversationUid the chat
     * @param messageId       the message
     * @param reason          why, or null
     */
    public void report(int accountId, UUID conversationUid, int messageId, @Nullable String reason) {
        Participation participation = guards.requireParticipant(accountId, conversationUid);
        Conversation chat = participation.conversation();
        StationScope scope = chat.station();
        if (scope == null) throw MessageRefusal.REPORT_NOT_A_STATION_CHAT.raise();
        Message message = guards.requireMessage(participation, messageId);
        if (!message.written() || message.deleted()) throw MessageRefusal.REPORT_NOT_TAKEN.raise();
        if (message.writtenBy(accountId)) throw MessageRefusal.REPORT_OWN_MESSAGE.raise();
        String kept = reasonOf(reason);
        if (!reports.report(messageId, accountId, kept)) return;
        log.info("Message {} in station chat {} reported by account {}", messageId, chat.id(), accountId);
        var audience = StationAudience.holders(scope.stationId(), StationPermission.MESSAGE_CHAT_MANAGE);
        var reporterMember = stationChats.memberAt(accountId, scope.stationId());
        notifier.notify(
                reporterMember.map(audience::except).orElse(audience),
                NotificationType.MESSAGE_REPORTED,
                NotificationData.of(
                        new NotificationParams.MessageReported(Objects.requireNonNullElse(chat.name(), "")),
                        NotificationLinks.messageReports()),
                Delivery.ONCE_WHILE_UNREAD);
    }

    /**
     * The open reports of the station's chats, each with the message in its context, the oldest
     * first.
     *
     * @param stationId the station
     * @param readerId  the account looking, who handles the station's chats
     * @return the reports
     */
    public List<ReportedMessage> open(int stationId, int readerId) {
        return reports.open(stationId).stream()
                .map(report -> new ReportedMessage(report, views.of(contextOf(report), readerId)))
                .toList();
    }

    /**
     * Removes a reported message for the station, which handles every open report of it.
     *
     * @param stationId the station
     * @param accountId the account handling the report
     * @param reportId  the report
     */
    public void remove(int stationId, int accountId, int reportId) {
        MessageReport report = requireOpen(stationId, reportId);
        moderation.remove(accountId, report.conversation(), report.messageId());
    }

    /**
     * Dismisses a report: the message stays, and every open report of it is set aside.
     *
     * @param stationId the station
     * @param accountId the account handling the report
     * @param reportId  the report
     */
    public void dismiss(int stationId, int accountId, int reportId) {
        MessageReport report = requireOpen(stationId, reportId);
        reports.handleAll(report.messageId(), accountId, ReportOutcome.DISMISSED);
        log.info("Reports of message {} dismissed by account {}", report.messageId(), accountId);
    }

    private MessageReport requireOpen(int stationId, int reportId) {
        return reports.findOpen(stationId, reportId).orElseThrow(MessageRefusal.REPORT_NOT_HERE::raise);
    }

    private List<Message> contextOf(MessageReport report) {
        int conversationId = report.conversation().id();
        List<Message> context = new ArrayList<>(messages.before(conversationId, report.messageId() + 1, CONTEXT + 1));
        context.addAll(messages.after(conversationId, report.messageId(), CONTEXT));
        return context;
    }

    private static @Nullable String reasonOf(@Nullable String reason) {
        if (reason == null || reason.isBlank()) return null;
        String kept = reason.strip();
        if (kept.length() > REASON_LENGTH) throw MessageRefusal.REPORT_REASON_TOO_LONG.raise();
        return kept;
    }
}
