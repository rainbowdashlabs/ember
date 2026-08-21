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
 * <p>Exactly one of the two recipients is set. A station member is the usual one; a cluster member is the
 * other, because the people who run a cluster hold no membership in any of its stations and a notification
 * addressed to them has nowhere else to go.
 *
 * @param id              unique identifier of the notification
 * @param memberId        the station member this notification is for, or {@code null}
 * @param clusterMemberId the cluster member this notification is for, or {@code null}
 * @param type            the notification category
 * @param data            localized message data and optional navigation link
 * @param createdAt       timestamp when the notification was created
 * @param acknowledgedAt  timestamp when the member acknowledged the notification, or {@code null}
 */
public record Notification(
        int id,
        Integer memberId,
        Integer clusterMemberId,
        NotificationType type,
        NotificationData data,
        Instant createdAt,
        Instant acknowledgedAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Notification> map() {
        return row -> {
            var type = row.getEnum("type", NotificationType.class);
            return new Notification(
                    row.getInt("id"),
                    row.getObject("member_id", Integer.class),
                    row.getObject("cluster_member_id", Integer.class),
                    type,
                    NotificationData.fromJson(row.getString("data"), type),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("acknowledged_at", INSTANT_TIMESTAMP));
        };
    }
}
