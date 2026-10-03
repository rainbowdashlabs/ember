/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.jspecify.annotations.Nullable;

/**
 * One block of a row.
 *
 * @param restriction who the block is shown to, or null for everybody. Only a letter evaluates it,
 *                    against the member the letter is for; pages, news and articles never carry one,
 *                    so their table keeps no column for it.
 */
public record ContentCell(
        int id,
        int rowId,
        int sortOrder,
        double widthPercent,
        CellContentType contentType,
        String content,
        CellConfig config,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable RestrictionAudience restriction) {

    /** A block shown to everybody, which is every block of a page, a news entry or an article. */
    public ContentCell(
            int id,
            int rowId,
            int sortOrder,
            double widthPercent,
            CellContentType contentType,
            String content,
            CellConfig config) {
        this(id, rowId, sortOrder, widthPercent, contentType, content, config, null);
    }

    public static RowMapping<ContentCell> map() {
        return row -> {
            var type = row.getEnum("content_type", CellContentType.class);
            return new ContentCell(
                    row.getInt("id"),
                    row.getInt("row_id"),
                    row.getInt("sort_order"),
                    row.getDouble("width_percent"),
                    type,
                    row.getString("content"),
                    CellConfig.parse(type, row.getString("config")));
        };
    }

    public ContentCell withContent(String content) {
        return new ContentCell(id, rowId, sortOrder, widthPercent, contentType, content, config, restriction);
    }

    public ContentCell withConfig(CellConfig config) {
        return new ContentCell(id, rowId, sortOrder, widthPercent, contentType, content, config, restriction);
    }
}
