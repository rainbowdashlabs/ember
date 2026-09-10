/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What happened to one attachment.
 *
 * @param id             the entry identifier
 * @param mailboxId      the mailbox it arrived in
 * @param stationId      the station it was filed for, which is what the duplicate check is scoped to
 * @param ruleId         the rule that took the message, or null where none did
 * @param ruleName       what that rule is called, read alongside for the page
 * @param messageId      the message it hung off
 * @param sender         who sent it
 * @param subject        what the message was called
 * @param attachmentName what the file was called in the mail
 * @param contentHash    the hash of its bytes, which is a duplicate key rather than a detail
 * @param outcome        what became of it
 * @param reason         the readable half of why, pruned after a year
 * @param documentId     the document that resulted, where one did
 * @param pruned         whether the readable fields have been cleared, leaving the row as a key
 * @param createdAt      when it was looked at
 */
public record MailImportEntry(
        int id,
        int mailboxId,
        int stationId,
        Integer ruleId,
        String ruleName,
        String messageId,
        String sender,
        String subject,
        String attachmentName,
        String contentHash,
        MailImportOutcome outcome,
        String reason,
        Integer documentId,
        boolean pruned,
        Instant createdAt) {

    public static RowMapping<MailImportEntry> map() {
        return row -> new MailImportEntry(
                row.getInt("id"),
                row.getInt("mailbox_id"),
                row.getInt("station_id"),
                row.getObject("rule_id", Integer.class),
                row.getString("rule_name"),
                row.getString("message_id"),
                row.getString("sender"),
                row.getString("subject"),
                row.getString("attachment_name"),
                row.getString("content_hash"),
                MailImportOutcome.valueOf(row.getString("outcome")),
                row.getString("reason"),
                row.getObject("document_id", Integer.class),
                row.getBoolean("pruned"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
