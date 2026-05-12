/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.UserSettings;
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

    public UserSettings update(int memberId, boolean notifyNews, boolean notifyNewEvents, boolean notifyEventStatus) {
        return Query.query("""
                        INSERT INTO user_settings(member_id, notify_news, notify_new_events, notify_event_status)
                        VALUES(:member_id, :notify_news, :notify_new_events, :notify_event_status)
                        ON CONFLICT (member_id) DO UPDATE
                            SET notify_news = :notify_news,
                                notify_new_events = :notify_new_events,
                                notify_event_status = :notify_event_status
                        RETURNING *;""")
                .single(Call.of()
                        .bind("member_id", memberId)
                        .bind("notify_news", notifyNews)
                        .bind("notify_new_events", notifyNewEvents)
                        .bind("notify_event_status", notifyEventStatus))
                .map(UserSettings.map())
                .first()
                .orElseThrow();
    }
}
