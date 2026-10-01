/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One test of a protocol on one day.
 *
 * @param createdBy the member who started it, or {@code null} once that member is gone
 */
public record TestProtocolRun(
        int id,
        int protocolId,
        int stationId,
        String name,
        LocalDate testDate,
        RunStatus status,
        @Nullable Integer createdBy,
        Instant createdAt) {

    public static RowMapping<TestProtocolRun> map() {
        return row -> new TestProtocolRun(
                row.getInt("id"),
                row.getInt("protocol_id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getDate("test_date").toLocalDate(),
                row.getEnum("status", RunStatus.class),
                row.getObject("created_by", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    public enum RunStatus {
        OPEN,
        CLOSED
    }
}
