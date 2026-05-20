/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A user-saved filter configuration for a specific table view.
 *
 * @param id         the filter identifier
 * @param accountId  the account that owns this filter
 * @param tableType  the table type this filter applies to (e.g. "members", "inventory")
 * @param name       the user-defined filter name
 * @param filterData the filter configuration stored as JSON
 * @param position   the display order position
 */
public record SavedFilter(int id, int accountId, String tableType, String name, String filterData, int position) {
    /** Creates a row mapping for database result set conversion. */
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
