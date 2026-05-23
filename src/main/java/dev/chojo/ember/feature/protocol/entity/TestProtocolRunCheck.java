/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record TestProtocolRunCheck(int runMemberId, int itemId, boolean checked, Integer checkedBy, Instant checkedAt) {

    public static RowMapping<TestProtocolRunCheck> map() {
        return row -> new TestProtocolRunCheck(
                row.getInt("run_member_id"),
                row.getInt("item_id"),
                row.getBoolean("checked"),
                row.getObject("checked_by", Integer.class),
                row.get("checked_at", INSTANT_TIMESTAMP));
    }
}
