/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A point of a protocol section that an examiner checks off.
 *
 * @param bonus whether it is a bonus point, which adds to the score when checked but not to any maximum
 */
public record TestProtocolItem(
        int id, int sectionId, String label, String description, double points, int position, boolean bonus) {

    public static RowMapping<TestProtocolItem> map() {
        return row -> new TestProtocolItem(
                row.getInt("id"),
                row.getInt("section_id"),
                row.getString("label"),
                row.getString("description"),
                row.getDouble("points"),
                row.getInt("position"),
                row.getBoolean("bonus"));
    }

    /** What the point adds to the maximum of its section: nothing for a bonus point. */
    public double maxPoints() {
        return bonus ? 0 : points;
    }
}
