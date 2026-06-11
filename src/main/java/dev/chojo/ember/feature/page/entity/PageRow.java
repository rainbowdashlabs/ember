/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.util.List;

public record PageRow(int id, int pageId, int sortOrder, List<PageCell> cells) {

    public static RowMapping<PageRow> mapFlat() {
        return row -> new PageRow(row.getInt("id"), row.getInt("page_id"), row.getInt("sort_order"), List.of());
    }

    public PageRow withCells(List<PageCell> cells) {
        return new PageRow(id, pageId, sortOrder, cells);
    }
}
