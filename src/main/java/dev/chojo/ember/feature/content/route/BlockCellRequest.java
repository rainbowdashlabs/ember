/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import tools.jackson.databind.JsonNode;

/**
 * One block as a page, news entry or knowledge-base article sends it.
 *
 * @param sortOrder    where the block stands in its row
 * @param widthPercent how much of the row it takes, the whole row where none is given
 * @param contentType  the kind of block
 * @param content      the block's text, where its kind has one
 * @param config       the block's settings as an object. Which record they are follows from the
 *                     content type beside them, so they are bound once that is known rather than
 *                     while the request is read.
 */
public record BlockCellRequest(
        int sortOrder, Double widthPercent, String contentType, String content, JsonNode config) {

    /**
     * The block as the block service takes it.
     *
     * <p>Settings that do not fit the kind of block are refused rather than saved as an empty block:
     * the author would otherwise lose them without a word the moment they saved.
     *
     * @throws io.javalin.http.HttpResponseException when the settings do not fit the kind of block
     */
    ContentBlockService.CellData toCellData() {
        var type = CellContentType.valueOf(contentType);
        return new ContentBlockService.CellData(
                sortOrder,
                widthPercent != null ? widthPercent : 100.0,
                type,
                content != null ? content : "",
                boundConfig(type));
    }

    private CellConfig boundConfig(CellContentType type) {
        try {
            return CellConfig.bind(type, config);
        } catch (IllegalArgumentException e) {
            throw Refusal.BLOCK_SETTINGS_REJECTED.raise(type.name());
        }
    }
}
