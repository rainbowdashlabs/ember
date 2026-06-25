/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import jakarta.inject.Singleton;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing per-member notification preferences (app and email toggles per notification type).
 */
@Singleton
public class NotificationSettingsRepository {

    /**
     * Retrieves all notification settings for a member.
     *
     * @param memberId the member ID
     * @return list of notification settings
     */
    public List<NotificationSetting> findByMember(int memberId) {
        return query("SELECT * FROM user_notification_settings WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(NotificationSetting.map())
                .all();
    }

    /**
     * Retrieves all notification settings for a member as a map keyed by notification type.
     *
     * @param memberId the member ID
     * @return map of notification type to setting
     */
    public Map<NotificationType, NotificationSetting> findByMemberAsMap(int memberId) {
        var list = findByMember(memberId);
        var map = new EnumMap<NotificationType, NotificationSetting>(NotificationType.class);
        for (var s : list) {
            map.put(s.notificationType(), s);
        }
        return map;
    }

    /**
     * Inserts or updates a notification setting for a member and type.
     *
     * @param memberId     the member ID
     * @param type         the notification type
     * @param appEnabled   whether in-app notifications are enabled
     * @param emailEnabled whether email notifications are enabled
     * @return the persisted setting
     */
    public NotificationSetting upsert(
            int memberId, NotificationType type, boolean appEnabled, boolean emailEnabled, boolean feedEnabled) {
        return query("""
                INSERT INTO user_notification_settings(member_id, notification_type, app_enabled, email_enabled, feed_enabled)
                VALUES(:member_id, :type, :app_enabled, :email_enabled, :feed_enabled)
                ON CONFLICT (member_id, notification_type) DO UPDATE SET
                    app_enabled = :app_enabled,
                    email_enabled = :email_enabled,
                    feed_enabled = :feed_enabled
                RETURNING *;""")
                .single(call().bind("member_id", memberId)
                        .bind("type", type)
                        .bind("app_enabled", appEnabled)
                        .bind("email_enabled", emailEnabled)
                        .bind("feed_enabled", feedEnabled))
                .map(NotificationSetting.map())
                .first()
                .orElseThrow();
    }

    /**
     * Bulk inserts or updates all notification settings for a member.
     *
     * @param memberId the member ID
     * @param settings map of notification type to setting
     */
    public void upsertAll(int memberId, Map<NotificationType, NotificationSetting> settings) {
        for (var entry : settings.entrySet()) {
            var s = entry.getValue();
            upsert(memberId, entry.getKey(), s.appEnabled(), s.emailEnabled(), s.feedEnabled());
        }
    }

    /**
     * Checks whether in-app notifications are enabled for a member and type.
     * Defaults to {@code true} if no setting exists.
     *
     * @param memberId the member ID
     * @param type     the notification type
     * @return {@code true} if app notifications are enabled
     */
    public boolean isAppEnabled(int memberId, NotificationType type) {
        return query("""
                SELECT app_enabled FROM user_notification_settings
                WHERE member_id = :member_id AND notification_type = :type;""")
                .single(call().bind("member_id", memberId).bind("type", type))
                .map(row -> row.getBoolean("app_enabled"))
                .first()
                .orElse(true); // default: app notifications enabled
    }

    /**
     * Checks whether email digest notifications are enabled for a member and type.
     * Defaults to {@code false} if no setting exists.
     *
     * @param memberId the member ID
     * @param type     the notification type
     * @return {@code true} if email notifications are enabled
     */
    public boolean isEmailEnabled(int memberId, NotificationType type) {
        return query("""
                SELECT email_enabled FROM user_notification_settings
                WHERE member_id = :member_id AND notification_type = :type;""")
                .single(call().bind("member_id", memberId).bind("type", type))
                .map(row -> row.getBoolean("email_enabled"))
                .first()
                .orElse(false); // default: email notifications disabled
    }
}
