/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.feed.entity.FeedToken;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class FeedTokenRepository {

    public Optional<FeedToken> findByMember(int memberId) {
        return Query.query("SELECT * FROM user_feed_token WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .map(FeedToken.map())
                .first();
    }

    public Optional<FeedToken> findByToken(String token) {
        return Query.query("SELECT * FROM user_feed_token WHERE token = :token;")
                .single(Call.of().bind("token", token))
                .map(FeedToken.map())
                .first();
    }

    public FeedToken create(int memberId, String token) {
        return Query.query("""
                        INSERT INTO user_feed_token(member_id, token) VALUES(:member_id, :token)
                        ON CONFLICT (member_id) DO UPDATE SET token = :token, created_at = now()
                        RETURNING *;""")
                .single(Call.of().bind("member_id", memberId).bind("token", token))
                .map(FeedToken.map())
                .first()
                .orElseThrow();
    }

    public boolean delete(int memberId) {
        return Query.query("DELETE FROM user_feed_token WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .update()
                .changed();
    }
}
