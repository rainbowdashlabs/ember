/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.UserSettings;
import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for user notification and communication settings.
 */
@Singleton
public class UserSettingsRepository {

    /**
     * Finds existing settings for a member or creates default settings if none exist.
     *
     * @param memberId the station member identifier
     * @return the member's settings
     */
    public UserSettings findOrCreate(int memberId) {
        return query("""
                INSERT INTO user_settings(member_id) VALUES(:member_id)
                ON CONFLICT (member_id) DO NOTHING
                RETURNING *;""")
                .single(call().bind("member_id", memberId))
                .map(UserSettings.map())
                .first()
                .orElseGet(() -> findByMemberId(memberId).orElseThrow());
    }

    /**
     * Finds settings for a member, returning empty if none exist.
     */
    public Optional<UserSettings> findByMemberId(int memberId) {
        return query("SELECT * FROM user_settings WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(UserSettings.map())
                .first();
    }

    /**
     * Updates the email notification preference for a member, creating settings if needed.
     *
     * @param memberId     the station member identifier
     * @param emailEnabled whether email notifications should be enabled
     * @return the updated settings
     */
    public UserSettings updateEmailEnabled(int memberId, boolean emailEnabled) {
        return query("""
                INSERT INTO user_settings(member_id, email_enabled) VALUES(:member_id, :email_enabled)
                ON CONFLICT (member_id) DO UPDATE SET email_enabled = :email_enabled
                RETURNING *;""")
                .single(call().bind("member_id", memberId).bind("email_enabled", emailEnabled))
                .map(UserSettings.map())
                .first()
                .orElseThrow();
    }

    public UserSettings updateTheme(int memberId, String theme, String darkMode, String feel) {
        return query("""
                INSERT INTO user_settings(member_id, theme, dark_mode, feel) VALUES(:member_id, :theme, :dark_mode, :feel)
                ON CONFLICT (member_id) DO UPDATE SET theme = :theme, dark_mode = :dark_mode, feel = :feel
                RETURNING *;""")
                .single(call().bind("member_id", memberId)
                        .bind("theme", theme)
                        .bind("dark_mode", darkMode)
                        .bind("feel", feel))
                .map(UserSettings.map())
                .first()
                .orElseThrow();
    }
}
