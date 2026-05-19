/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record NotificationSetting(
        int memberId, NotificationType notificationType, boolean appEnabled, boolean emailEnabled) {
    public static RowMapping<NotificationSetting> map() {
        return row -> new NotificationSetting(
                row.getInt("member_id"),
                NotificationType.valueOf(row.getString("notification_type")),
                row.getBoolean("app_enabled"),
                row.getBoolean("email_enabled"));
    }
}
