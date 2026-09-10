/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.mailimport.entity.MailOrigin;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Where documents came from, for the ones that arrived by mail.
 */
@Singleton
public class MailOriginRepository {

    private static final String COLUMNS = "document_id, mailbox_id, sender, subject, received_at";

    /** Writes the provenance of a document as it is filed. */
    public MailOrigin create(int documentId, Integer mailboxId, String sender, String subject, Instant receivedAt) {
        return query("""
                        INSERT INTO member_document_mail_origin(document_id, mailbox_id, sender, subject, received_at)
                        VALUES (:document_id, :mailbox_id, :sender, :subject, :received_at)
                        ON CONFLICT (document_id) DO UPDATE
                            SET mailbox_id = :mailbox_id, sender = :sender, subject = :subject,
                                received_at = :received_at
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("document_id", documentId)
                        .bind("mailbox_id", mailboxId)
                        .bind("sender", sender)
                        .bind("subject", subject)
                        .bind("received_at", receivedAt, INSTANT_TIMESTAMP))
                .map(MailOrigin.map())
                .first()
                .orElseThrow();
    }

    public Optional<MailOrigin> findByDocument(int documentId) {
        return query("SELECT %s FROM member_document_mail_origin WHERE document_id = :document_id;", COLUMNS)
                .single(call().bind("document_id", documentId))
                .map(MailOrigin.map())
                .first();
    }

    /**
     * The provenance of several documents at once, so a page listing them does not ask per tile.
     *
     * @param documentIds the documents on the page
     */
    public List<MailOrigin> findByDocuments(List<Integer> documentIds) {
        if (documentIds.isEmpty()) return List.of();
        return query("""
                        SELECT %s FROM member_document_mail_origin
                        WHERE document_id = ANY (:document_ids);""", COLUMNS)
                .single(call().bind("document_ids", documentIds, PostgreSqlTypes.INTEGER))
                .map(MailOrigin.map())
                .all();
    }
}
