/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardTicketFieldValue(int ticketId, int fieldId, BoardFieldType fieldType, BoardFieldValue value) {

    public static RowMapping<BoardTicketFieldValue> map() {
        return row -> {
            var fieldType = row.getEnum("field_type", BoardFieldType.class);
            return new BoardTicketFieldValue(
                    row.getInt("ticket_id"),
                    row.getInt("field_id"),
                    fieldType,
                    BoardFieldValue.parse(fieldType, row.getString("value")));
        };
    }

    public String valueToJson() {
        return value != null ? value.toJson() : "null";
    }
}
