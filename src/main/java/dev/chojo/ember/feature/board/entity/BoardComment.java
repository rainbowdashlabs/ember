/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record BoardComment(
        int id,
        int ticketId,
        Integer parentId,
        Integer authorId,
        String content,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt) {

    public static RowMapping<BoardComment> map() {
        return row -> new BoardComment(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getObject("parent_id", Integer.class),
                row.getObject("author_id", Integer.class),
                row.getString("content"),
                row.getBoolean("deleted"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
