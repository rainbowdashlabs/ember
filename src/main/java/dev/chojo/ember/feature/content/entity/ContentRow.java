/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.util.List;

/**
 * One row of blocks inside a container. Cells sit side by side in a row; rows stack down the page.
 */
public record ContentRow(int id, int containerId, int sortOrder, List<ContentCell> cells) {

    public static RowMapping<ContentRow> mapFlat() {
        return row -> new ContentRow(row.getInt("id"), row.getInt("container_id"), row.getInt("sort_order"), List.of());
    }

    public ContentRow withCells(List<ContentCell> cells) {
        return new ContentRow(id, containerId, sortOrder, cells);
    }
}
