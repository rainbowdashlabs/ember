/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an in-app notification sent to a member.
 *
 * @param id             unique identifier of the notification
 * @param memberId       the member this notification is for
 * @param type           the notification category
 * @param data           localized message data and optional navigation link
 * @param createdAt      timestamp when the notification was created
 * @param acknowledgedAt timestamp when the member acknowledged the notification, or {@code null}
 */
public record Notification(
        int id, int memberId, NotificationType type, NotificationData data, Instant createdAt, Instant acknowledgedAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Notification> map() {
        return row -> {
            var type = row.getEnum("type", NotificationType.class);
            return new Notification(
                    row.getInt("id"),
                    row.getInt("member_id"),
                    type,
                    NotificationData.fromJson(row.getString("data"), type),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("acknowledged_at", INSTANT_TIMESTAMP));
        };
    }
}
