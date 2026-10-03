/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.route;

import dev.chojo.ember.feature.content.service.ContentBlockService;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One row of blocks as a page, news entry or knowledge-base article sends it.
 *
 * @param sortOrder   where the row stands
 * @param cells       the blocks side by side in it
 * @param columnLines whether a line is drawn between its columns, none where left out. Only a letter
 *                    keeps it.
 */
public record BlockRowRequest(
        int sortOrder,
        List<BlockCellRequest> cells,
        @Nullable Boolean columnLines) {

    /** A row without lines between its columns, as a page, a news entry or an article sends it. */
    public BlockRowRequest(int sortOrder, List<BlockCellRequest> cells) {
        this(sortOrder, cells, null);
    }

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
                        : cells.stream().map(BlockCellRequest::toCellData).toList(),
                Boolean.TRUE.equals(columnLines));
    }
}
