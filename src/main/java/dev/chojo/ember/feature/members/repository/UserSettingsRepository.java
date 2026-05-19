/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.members.entity.UserSettings;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class UserSettingsRepository {

    public UserSettings findOrCreate(int memberId) {
        return Query.query("""
                            INSERT INTO user_settings(member_id) VALUES(:member_id)
                            ON CONFLICT (member_id) DO NOTHING
                            RETURNING *;""")
                .single(Call.of().bind("member_id", memberId))
                .map(UserSettings.map())
                .first()
                .orElseGet(() -> findByMemberId(memberId).orElseThrow());
    }

    public Optional<UserSettings> findByMemberId(int memberId) {
        return Query.query("SELECT * FROM user_settings WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .map(UserSettings.map())
                .first();
    }

    public UserSettings updateEmailEnabled(int memberId, boolean emailEnabled) {
        return Query.query("""
                            INSERT INTO user_settings(member_id, email_enabled) VALUES(:member_id, :email_enabled)
                            ON CONFLICT (member_id) DO UPDATE SET email_enabled = :email_enabled
                            RETURNING *;""")
                .single(Call.of().bind("member_id", memberId).bind("email_enabled", emailEnabled))
                .map(UserSettings.map())
                .first()
                .orElseThrow();
    }
}
