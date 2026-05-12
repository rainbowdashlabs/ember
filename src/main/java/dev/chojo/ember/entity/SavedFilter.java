/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record SavedFilter(int id, int accountId, String tableType, String name, String filterData, int position) {
    public static RowMapping<SavedFilter> map() {
        return row -> new SavedFilter(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("table_type"),
                row.getString("name"),
                row.getString("filter_data"),
                row.getInt("position"));
    }
}
