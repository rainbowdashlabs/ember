/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.MessageReport;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.ReportOutcome;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** The messages members reported to the people handling a station's chats. */
@Singleton
public class MessageReportRepository {
    private static final String COLUMNS = """
            r.id AS report_id, r.message_id AS report_message_id, r.reason, r.reported_content, r.created_at AS reported_at, \
            %s, %s""".formatted(Conversation.COLUMNS, Person.columns("ra", "reporter_"));

    private static final String FROM = """
            FROM message_report r
            JOIN message msg ON msg.id = r.message_id
            JOIN conversation c ON c.id = msg.conversation_id
            LEFT JOIN account ra ON ra.id = r.reporter_account_id""";

    /**
     * Reports a message, keeping its text as it reads now. A second report of the same message by the
     * same account while the first is open changes nothing, and neither does a report of a message
     * deleted meanwhile.
     *
     * @param messageId  the message
     * @param reporterId the account reporting it
     * @param reason     why, or null
     * @return whether a new report was made
     */
    public boolean report(int messageId, int reporterId, @Nullable String reason) {
        return query("""
                INSERT INTO message_report(message_id, reporter_account_id, reason, reported_content)
                SELECT id, :reporter_id, :reason, content FROM message
                WHERE id = :message_id AND deleted_at IS NULL
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("message_id", messageId)
                        .bind("reporter_id", reporterId)
                        .bind("reason", reason))
                .insert()
                .changed();
    }

    /**
     * The open reports of a station's chats, the oldest first.
     *
     * @param stationId the station
     * @return the reports
     */
    public List<MessageReport> open(int stationId) {
        return query("""
                SELECT %s %s
                WHERE c.station_id = :station_id AND r.handled_at IS NULL
                ORDER BY r.created_at, r.id;""", COLUMNS, FROM)
                .single(call().bind("station_id", stationId))
                .map(MessageReport.map())
                .all();
    }

    /**
     * An open report of a station's chats.
     *
     * @param stationId the station
     * @param reportId  the report
     * @return the report, or empty where it is handled, of another station or not there
     */
    public Optional<MessageReport> findOpen(int stationId, int reportId) {
        return query("""
                SELECT %s %s
                WHERE c.station_id = :station_id AND r.id = :id AND r.handled_at IS NULL;""", COLUMNS, FROM)
                .single(call().bind("station_id", stationId).bind("id", reportId))
                .map(MessageReport.map())
                .first();
    }

    /**
     * Handles every open report of a message the same way.
     *
     * @param messageId   the message
     * @param byAccountId who handled them
     * @param outcome     how
     * @return how many reports were handled
     */
    public int handleAll(int messageId, int byAccountId, ReportOutcome outcome) {
        return query("""
                UPDATE message_report SET handled_at = now(), handled_by_account_id = :by, outcome = :outcome
                WHERE message_id = :message_id AND handled_at IS NULL;""")
                .single(call().bind("message_id", messageId)
                        .bind("by", byAccountId)
                        .bind("outcome", outcome))
                .update()
                .rows();
    }
}
