/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record TestProtocolRun(
        int id,
        int protocolId,
        int stationId,
        String name,
        LocalDate testDate,
        RunStatus status,
        int createdBy,
        Instant createdAt) {

    public enum RunStatus {
        OPEN,
        CLOSED
    }

    public static RowMapping<TestProtocolRun> map() {
        return row -> new TestProtocolRun(
                row.getInt("id"),
                row.getInt("protocol_id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getDate("test_date").toLocalDate(),
                RunStatus.valueOf(row.getString("status")),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
