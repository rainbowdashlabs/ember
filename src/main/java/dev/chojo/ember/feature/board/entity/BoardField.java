/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardField(int id, int boardId, String name, String fieldType, BoardFieldConfig config, int position) {

    public static RowMapping<BoardField> map() {
        return row -> new BoardField(
                row.getInt("id"),
                row.getInt("board_id"),
                row.getString("name"),
                row.getString("field_type"),
                BoardFieldConfig.parse(row.getString("config")),
                row.getInt("position"));
    }
}
