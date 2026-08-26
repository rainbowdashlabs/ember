/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.inventory.entity.ItemMovementDocument;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the files attached to a movement as evidence.
 */
@Singleton
public class ItemMovementDocumentRepository {
    private static final String DOCUMENT_COLUMNS =
            "id, movement_id, file_name, mime_type, size_bytes, uploaded_by, created_at";

    public ItemMovementDocument create(
            int movementId, String fileName, String mimeType, long sizeBytes, Integer uploadedBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO item_movement_document(movement_id, file_name, mime_type, size_bytes, uploaded_by)
                VALUES (:movement_id, :file_name, :mime_type, :size_bytes, :uploaded_by)
                RETURNING %s;""",
                call().bind("movement_id", movementId)
                        .bind("file_name", fileName)
                        .bind("mime_type", mimeType)
                        .bind("size_bytes", sizeBytes)
                        .bind("uploaded_by", uploadedBy),
                ItemMovementDocument.map(),
                DOCUMENT_COLUMNS);
    }

    /**
     * The file attached to a movement. A movement carries at most one, because it is evidence for one request
     * rather than a folder about it.
     */
    public Optional<ItemMovementDocument> findByMovement(int movementId) {
        return query("""
                SELECT %s FROM item_movement_document WHERE movement_id = :movement_id ORDER BY id LIMIT 1;""", DOCUMENT_COLUMNS)
                .single(call().bind("movement_id", movementId))
                .map(ItemMovementDocument.map())
                .first();
    }
}
