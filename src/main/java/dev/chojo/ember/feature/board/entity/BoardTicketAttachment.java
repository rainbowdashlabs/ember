/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * @param uploaderStationUid UUID of the uploader's station. Together with {@link #uploaderMemberUid}
 *                           this identifies the author, including federated members from other stations.
 * @param uploaderMemberUid  UUID of the uploading member within their station.
 */
public record BoardTicketAttachment(
        int id,
        int ticketId,
        String filename,
        String originalName,
        String contentType,
        long sizeBytes,
        UUID uploaderStationUid,
        UUID uploaderMemberUid,
        Instant createdAt) {

    public static RowMapping<BoardTicketAttachment> map() {
        return row -> new BoardTicketAttachment(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getString("filename"),
                row.getString("original_name"),
                row.getString("content_type"),
                row.getLong("size_bytes"),
                row.get("uploader_station_uid", StandardValueConverter.UUID_STRING),
                row.get("uploader_member_uid", StandardValueConverter.UUID_STRING),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
