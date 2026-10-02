/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.route;

import dev.chojo.ember.feature.content.service.ContentBlockService;

import java.util.List;

/**
 * Request body for saving the blocks of a news entry or a knowledge-base article.
 *
 * @param rows the rows of blocks, top to bottom
 */
public record SaveBlocksRequest(List<BlockRowRequest> rows) {

    /**
     * The rows as the block service takes them.
     *
     * @throws io.javalin.http.HttpResponseException when a block's settings do not fit its kind
     */
    public List<ContentBlockService.RowData> toRowData() {
        return BlockRowRequest.toRowData(rows);
    }
}
