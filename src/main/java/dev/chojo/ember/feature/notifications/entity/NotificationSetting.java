/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a member's preference for a specific notification type,
 * controlling whether in-app and email notifications are enabled.
 *
 * @param memberId         the member this setting belongs to
 * @param notificationType the notification category
 * @param appEnabled       whether in-app notifications are enabled
 * @param emailEnabled     whether email digest notifications are enabled
 */
public record NotificationSetting(
        int memberId,
        NotificationType notificationType,
        boolean appEnabled,
        boolean emailEnabled,
        boolean feedEnabled) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<NotificationSetting> map() {
        return row -> new NotificationSetting(
                row.getInt("member_id"),
                row.getEnum("notification_type", NotificationType.class),
                row.getBoolean("app_enabled"),
                row.getBoolean("email_enabled"),
                row.getBoolean("feed_enabled"));
    }
}
