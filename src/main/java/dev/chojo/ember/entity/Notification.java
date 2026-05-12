/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record Notification(
        int id,
        int memberId,
        NotificationType type,
        Integer referenceId,
        String message,
        Instant createdAt,
        Instant acknowledgedAt) {
    public static RowMapping<Notification> map() {
        return row -> new Notification(
                row.getInt("id"),
                row.getInt("member_id"),
                row.getEnum("type", NotificationType.class),
                row.getObject("reference_id", Integer.class),
                row.getString("message"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("acknowledged_at", INSTANT_TIMESTAMP));
    }
}
