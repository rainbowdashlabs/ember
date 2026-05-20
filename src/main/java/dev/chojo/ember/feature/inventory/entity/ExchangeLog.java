/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a status change log entry for an equipment exchange request.
 *
 * @param id        the unique log entry identifier
 * @param requestId the exchange request this log entry belongs to
 * @param oldStatus the status before the change
 * @param newStatus the status after the change
 * @param changedBy the member who made the status change
 * @param changedAt when the status change occurred
 * @param note      an optional note explaining the change
 */
public record ExchangeLog(
        int id,
        int requestId,
        ExchangeStatus oldStatus,
        ExchangeStatus newStatus,
        int changedBy,
        Instant changedAt,
        String note) {
    /**
     * Creates a row mapping for database result set conversion.
     */
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
