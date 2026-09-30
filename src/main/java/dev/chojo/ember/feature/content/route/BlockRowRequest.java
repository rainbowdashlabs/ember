/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.route;

import dev.chojo.ember.feature.content.service.ContentBlockService;

import java.util.List;

/**
 * One row of blocks as a page, news entry or knowledge-base article sends it.
 *
 * @param sortOrder where the row stands
 * @param cells     the blocks side by side in it
 */
public record BlockRowRequest(int sortOrder, List<BlockCellRequest> cells) {

    /**
     * The rows as the block service takes them, none where none were sent.
     *
     * @throws io.javalin.http.HttpResponseException when a block's settings do not fit its kind
     */
    public static List<ContentBlockService.RowData> toRowData(List<BlockRowRequest> rows) {
        if (rows == null) return List.of();
        return rows.stream().map(BlockRowRequest::toRowData).toList();
    }

    private ContentBlockService.RowData toRowData() {
        return new ContentBlockService.RowData(
                sortOrder,
                cells == null
                        ? List.of()
                        : cells.stream().map(BlockCellRequest::toCellData).toList());
    }
}
