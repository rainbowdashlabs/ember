/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

public record BoardLane(
        int id, int boardId, String name, @Nullable String color, int position) {

    public static RowMapping<BoardLane> map() {
        return row -> new BoardLane(
                row.getInt("id"),
                row.getInt("board_id"),
                row.getString("name"),
                row.getString("color"),
                row.getInt("position"));
    }
}
