/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record TestProtocolItem(int id, int sectionId, String label, String description, double points, int position) {

    public static RowMapping<TestProtocolItem> map() {
        return row -> new TestProtocolItem(
                row.getInt("id"),
                row.getInt("section_id"),
                row.getString("label"),
                row.getString("description"),
                row.getDouble("points"),
                row.getInt("position"));
    }
}
