/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record BoardTicketAttachment(
        int id,
        int ticketId,
        String filename,
        String originalName,
        String contentType,
        long sizeBytes,
        int uploadedBy,
        Instant createdAt) {

    public static RowMapping<BoardTicketAttachment> map() {
        return row -> new BoardTicketAttachment(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getString("filename"),
                row.getString("original_name"),
                row.getString("content_type"),
                row.getLong("size_bytes"),
                row.getInt("uploaded_by"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
