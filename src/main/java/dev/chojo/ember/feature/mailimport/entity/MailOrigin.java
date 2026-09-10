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
 * Where a document came from, where it came by mail.
 *
 * <p>A document filed by the importer has no uploader, because nobody uploaded it. Without this the
 * detail shows an empty uploader and says nothing; with it, it can say the document arrived from that
 * address on that day.
 *
 * <p>It belongs to the document rather than to the import log. Leaving it in the log would mean a
 * document losing its own history on the day the log was pruned, which is the sort of contradiction that
 * only shows up a year after release.
 *
 * @param documentId the document that arrived by mail
 * @param mailboxId  which mailbox brought it, or null once that mailbox is gone
 * @param sender     the address it came from
 * @param subject    what the message was called
 * @param receivedAt when the mail arrived
 */
public record MailOrigin(int documentId, Integer mailboxId, String sender, String subject, Instant receivedAt) {

    public static RowMapping<MailOrigin> map() {
        return row -> new MailOrigin(
                row.getInt("document_id"),
                row.getObject("mailbox_id", Integer.class),
                row.getString("sender"),
                row.getString("subject"),
                row.get("received_at", INSTANT_TIMESTAMP));
    }
}
