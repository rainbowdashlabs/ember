/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record FederationBoardShare(int id, int boardId) {

    public static RowMapping<FederationBoardShare> map() {
        return row -> new FederationBoardShare(row.getInt("id"), row.getInt("board_id"));
    }
}
