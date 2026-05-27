/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardLabel(int id, int boardId, String name, String color) {

    public static RowMapping<BoardLabel> map() {
        return row ->
                new BoardLabel(row.getInt("id"), row.getInt("board_id"), row.getString("name"), row.getString("color"));
    }
}
