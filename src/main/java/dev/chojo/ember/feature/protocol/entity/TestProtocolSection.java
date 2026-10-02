/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

public record TestProtocolSection(
        int id,
        int protocolId,
        @Nullable Integer parentId,
        String name,
        String description,
        @Nullable Integer maxPoints,
        @Nullable Integer passThreshold,
        int position) {

    public static RowMapping<TestProtocolSection> map() {
        return row -> new TestProtocolSection(
                row.getInt("id"),
                row.getInt("protocol_id"),
                row.getObject("parent_id", Integer.class),
                row.getString("name"),
                row.getString("description"),
                row.getObject("max_points", Integer.class),
                row.getObject("pass_threshold", Integer.class),
                row.getInt("position"));
    }
}
