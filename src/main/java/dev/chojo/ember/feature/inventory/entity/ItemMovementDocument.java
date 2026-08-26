/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A file attached to one movement as evidence for it.
 *
 * <p>It hangs off the movement rather than off the item or the member, because it is evidence for this one
 * request. Opening the movement shows who raised it, what was written on both sides and what came with it,
 * all in one place, and it goes when the movement goes.
 *
 * @param id         the document identifier
 * @param movementId the movement it belongs to
 * @param fileName   the name it was uploaded under, used when it is served back
 * @param mimeType   the media type it was uploaded as
 * @param sizeBytes  how large it is
 * @param uploadedBy the member who attached it, or {@code null} once that membership is gone
 * @param createdAt  when it was attached
 */
public record ItemMovementDocument(
        int id,
        int movementId,
        String fileName,
        String mimeType,
        long sizeBytes,
        Integer uploadedBy,
        Instant createdAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ItemMovementDocument> map() {
        return row -> new ItemMovementDocument(
                row.getInt("id"),
                row.getInt("movement_id"),
                row.getString("file_name"),
                row.getString("mime_type"),
                row.getLong("size_bytes"),
                row.getObject("uploaded_by", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
