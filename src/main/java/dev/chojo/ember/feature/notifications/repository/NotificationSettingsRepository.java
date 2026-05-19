/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import jakarta.inject.Singleton;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Singleton
public class NotificationSettingsRepository {

    public List<NotificationSetting> findByMember(int memberId) {
        return Query.query("SELECT * FROM user_notification_settings WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .map(NotificationSetting.map())
                .all();
    }

    public Map<NotificationType, NotificationSetting> findByMemberAsMap(int memberId) {
        var list = findByMember(memberId);
        var map = new EnumMap<NotificationType, NotificationSetting>(NotificationType.class);
        for (var s : list) {
            map.put(s.notificationType(), s);
        }
        return map;
    }

    public NotificationSetting upsert(int memberId, NotificationType type, boolean appEnabled, boolean emailEnabled) {
        return Query.query("""
                            INSERT INTO user_notification_settings(member_id, notification_type, app_enabled, email_enabled)
                            VALUES(:member_id, :type, :app_enabled, :email_enabled)
                            ON CONFLICT (member_id, notification_type) DO UPDATE SET
                                app_enabled = :app_enabled,
                                email_enabled = :email_enabled
                            RETURNING *;""")
                .single(Call.of()
                        .bind("member_id", memberId)
                        .bind("type", type.name())
                        .bind("app_enabled", appEnabled)
                        .bind("email_enabled", emailEnabled))
                .map(NotificationSetting.map())
                .first()
                .orElseThrow();
    }

    public void upsertAll(int memberId, Map<NotificationType, NotificationSetting> settings) {
        for (var entry : settings.entrySet()) {
            var s = entry.getValue();
            upsert(memberId, entry.getKey(), s.appEnabled(), s.emailEnabled());
        }
    }

    public boolean isAppEnabled(int memberId, NotificationType type) {
        return Query.query("""
                            SELECT app_enabled FROM user_notification_settings
                            WHERE member_id = :member_id AND notification_type = :type;""")
                .single(Call.of().bind("member_id", memberId).bind("type", type.name()))
                .map(row -> row.getBoolean("app_enabled"))
                .first()
                .orElse(true); // default: app notifications enabled
    }

    public boolean isEmailEnabled(int memberId, NotificationType type) {
        return Query.query("""
                            SELECT email_enabled FROM user_notification_settings
                            WHERE member_id = :member_id AND notification_type = :type;""")
                .single(Call.of().bind("member_id", memberId).bind("type", type.name()))
                .map(row -> row.getBoolean("email_enabled"))
                .first()
                .orElse(false); // default: email notifications disabled
    }
}
