/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.repository;

import dev.chojo.ember.feature.mailimport.entity.MailImportEntry;
import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.count;

/**
 * What was looked at and what became of it, and the two keys that stop anything being done twice.
 *
 * <p>The two keys answer two different questions and neither replaces the other. The content hash says
 * these same bytes are already here, whatever message carried them, which is what catches a forward: a
 * forwarded mail gets a new identifier, so a key needing both would let it through. The message
 * identifier says this message has been dealt with whatever came of it, which is what survives a
 * connection dropping after the import but before the flags were written.
 *
 * <p>Both outlive the readable half of the log. Pruning clears the subject and the reason after a year
 * and leaves the keys, because a mail redelivered after the pruning would otherwise become a second
 * document.
 */
@Singleton
public class MailImportLogRepository {

    private static final String COLUMNS = """
            e.id, e.mailbox_id, e.station_id, e.rule_id, r.name AS rule_name, e.message_id, e.sender, e.subject,
            e.attachment_name, e.content_hash, e.outcome, e.reason, e.document_id, e.pruned, e.created_at""";

    private static final String FROM = """
            FROM mail_import_entry e LEFT JOIN mail_rule r ON r.id = e.rule_id""";

    /** Writes down what became of one attachment. */
    public MailImportEntry record(
            int mailboxId,
            int stationId,
            Integer ruleId,
            String messageId,
            String sender,
            String subject,
            String attachmentName,
            String contentHash,
            MailImportOutcome outcome,
            String reason,
            Integer documentId) {
        int id = query("""
                        INSERT INTO mail_import_entry(mailbox_id, station_id, rule_id, message_id, sender, subject,
                                                      attachment_name, content_hash, outcome, reason, document_id)
                        VALUES (:mailbox_id, :station_id, :rule_id, :message_id, :sender, :subject,
                                :attachment_name, :content_hash, :outcome, :reason, :document_id)
                        RETURNING id;""")
                .single(call().bind("mailbox_id", mailboxId)
                        .bind("station_id", stationId)
                        .bind("rule_id", ruleId)
                        .bind("message_id", messageId)
                        .bind("sender", sender)
                        .bind("subject", subject)
                        .bind("attachment_name", attachmentName)
                        .bind("content_hash", contentHash)
                        .bind("outcome", outcome.name())
                        .bind("reason", reason)
                        .bind("document_id", documentId))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        return findById(id).orElseThrow();
    }

    public java.util.Optional<MailImportEntry> findById(int id) {
        return query("SELECT %s %s WHERE e.id = :id;", COLUMNS, FROM)
                .single(call().bind("id", id))
                .map(MailImportEntry.map())
                .first();
    }

    /** The log of one station, newest first, which is the order somebody debugging an import reads it in. */
    public List<MailImportEntry> findByStation(int stationId, int limit, int offset) {
        return query("""
                        SELECT %s %s WHERE e.station_id = :station_id
                        ORDER BY e.created_at DESC, e.id DESC LIMIT :limit OFFSET :offset;""", COLUMNS, FROM)
                .single(call().bind("station_id", stationId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(MailImportEntry.map())
                .all();
    }

    public int countByStation(int stationId) {
        return count(
                "SELECT count(*) FROM mail_import_entry WHERE station_id = :station_id;",
                call().bind("station_id", stationId));
    }

    /**
     * Whether these exact bytes have already been filed for this station.
     *
     * <p>Scoped to the station rather than to the mailbox, because the same file arriving in two of a
     * station's mailboxes is still the same document. The price of the key being the bytes alone is that
     * deliberately re-sending an identical file produces no second document, which is the right answer.
     */
    public boolean hasImportedContent(int stationId, String contentHash) {
        return count("""
                        SELECT count(*) FROM mail_import_entry
                        WHERE station_id = :station_id AND content_hash = :content_hash AND outcome = 'IMPORTED';""", call().bind("station_id", stationId).bind("content_hash", contentHash)) > 0;
    }

    /**
     * Whether this mailbox has already dealt with this message, whatever came of it.
     *
     * @param messageId the message's own identifier, or one made from its envelope where it had none
     */
    public boolean hasHandledMessage(int mailboxId, String messageId) {
        return count("""
                        SELECT count(*) FROM mail_handled_message
                        WHERE mailbox_id = :mailbox_id AND message_id = :message_id;""", call().bind("mailbox_id", mailboxId).bind("message_id", messageId)) > 0;
    }

    /**
     * Writes down that a message was dealt with.
     *
     * @param derived whether the identifier was made from the envelope because the message carried none
     */
    public void markMessageHandled(int mailboxId, String messageId, boolean derived) {
        query("""
                        INSERT INTO mail_handled_message(mailbox_id, message_id, derived)
                        VALUES (:mailbox_id, :message_id, :derived)
                        ON CONFLICT (mailbox_id, message_id) DO NOTHING;""")
                .single(call().bind("mailbox_id", mailboxId)
                        .bind("message_id", messageId)
                        .bind("derived", derived))
                .update();
    }

    /**
     * Clears the readable half of everything older than the cutoff, leaving the keys.
     *
     * <p>What a reader looks at is the subject and the reason, and a year of those is plenty. The hash
     * and the message identifier are not touched, so a mail redelivered after this has run is still
     * recognised rather than filed again.
     *
     * @return how many entries were reduced to their keys
     */
    public int pruneReadableBefore(Instant cutoff) {
        return query("""
                        UPDATE mail_import_entry
                        SET subject = NULL, reason = NULL, sender = NULL, attachment_name = NULL, pruned = TRUE
                        WHERE created_at < :cutoff AND pruned = FALSE;""")
                .single(call().bind("cutoff", cutoff, INSTANT_TIMESTAMP))
                .update()
                .rows();
    }

    /**
     * How many documents this station has been sent that nobody has bound to a member yet.
     *
     * <p>Filing under nobody is the ordinary outcome, so this is not an error count. It is what the
     * digest tells whoever may read documents, because the thing nobody notices is a store quietly
     * filling with paperwork waiting to be sorted.
     */
    public int countUnboundSince(int stationId, Instant since) {
        return count("""
                SELECT count(*) FROM mail_import_entry e
                JOIN member_document d ON d.id = e.document_id
                LEFT JOIN member_document_member m ON m.document_id = d.id
                WHERE e.station_id = :station_id AND e.outcome = 'IMPORTED'
                  AND e.created_at >= :since AND m.member_id IS NULL;""", call().bind("station_id", stationId).bind("since", since, INSTANT_TIMESTAMP));
    }
}
