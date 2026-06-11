/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.repository;

import dev.chojo.ember.feature.feed.entity.FeedToken;
import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class FeedTokenRepository {

    public Optional<FeedToken> findByMember(int memberId) {
        return query("SELECT * FROM user_feed_token WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(FeedToken.map())
                .first();
    }

    public Optional<FeedToken> findByToken(String token) {
        return query("SELECT * FROM user_feed_token WHERE token = :token;")
                .single(call().bind("token", token))
                .map(FeedToken.map())
                .first();
    }

    public FeedToken create(int memberId, String token) {
        return query("""
                        INSERT INTO user_feed_token(member_id, token) VALUES(:member_id, :token)
                        ON CONFLICT (member_id) DO UPDATE SET token = :token, created_at = now()
                        RETURNING *;""")
                .single(call().bind("member_id", memberId).bind("token", token))
                .map(FeedToken.map())
                .first()
                .orElseThrow();
    }

    public void updateIcalPolled(int memberId) {
        query("UPDATE user_feed_token SET ical_polled_at = now() WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .update();
    }

    public void updateNotificationPolled(int memberId) {
        query("UPDATE user_feed_token SET notification_polled_at = now() WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .update();
    }

    public boolean delete(int memberId) {
        return query("DELETE FROM user_feed_token WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .update()
                .changed();
    }
}
