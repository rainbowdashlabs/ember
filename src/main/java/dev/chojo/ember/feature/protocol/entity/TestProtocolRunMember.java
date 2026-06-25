/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record TestProtocolRunMember(
        int id, int runId, int memberId, Integer lockedBy, Instant lockedAt, boolean completed, double totalScore) {

    public static RowMapping<TestProtocolRunMember> map() {
        return row -> new TestProtocolRunMember(
                row.getInt("id"),
                row.getInt("run_id"),
                row.getInt("member_id"),
                row.getObject("locked_by", Integer.class),
                row.get("locked_at", INSTANT_TIMESTAMP),
                row.getBoolean("completed"),
                row.getDouble("total_score"));
    }

    public boolean isLocked() {
        return lockedBy != null;
    }
}
