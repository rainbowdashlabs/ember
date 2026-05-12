/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record ExchangeLog(int id, int requestId, ExchangeStatus oldStatus, ExchangeStatus newStatus,
                           int changedBy, Instant changedAt, String note) {
    public static RowMapping<ExchangeLog> map() {
        return row -> new ExchangeLog(
                row.getInt("id"),
                row.getInt("request_id"),
                row.getEnum("old_status", ExchangeStatus.class),
                row.getEnum("new_status", ExchangeStatus.class),
                row.getInt("changed_by"),
                row.get("changed_at", INSTANT_TIMESTAMP),
                row.getString("note"));
    }
}
