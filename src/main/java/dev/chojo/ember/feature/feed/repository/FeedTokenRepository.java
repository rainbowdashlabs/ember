/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.repository;

import dev.chojo.ember.feature.feed.entity.FeedToken;
import dev.chojo.ember.feature.feed.entity.FeedUse;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

@Singleton
public class FeedTokenRepository {
    private static final String USER_FEED_TOKEN_COLUMNS =
            "member_id, token, created_at, ical_polled_at, notification_polled_at";

    public Optional<FeedToken> findByMember(int memberId) {
        return query("SELECT %s FROM user_feed_token WHERE member_id = :member_id;", USER_FEED_TOKEN_COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(FeedToken.map())
                .first();
    }

    /**
     * Every standing subscription at one station, newest first.
     *
     * <p>The token is left out of the projection rather than dropped afterwards, so it cannot reach
     * a response by somebody adding a field to the record it maps into.
     *
     * @param stationId the station whose members are asked about
     * @return one row per member who has set a subscription up
     */
    public List<FeedUse> findUseByStation(int stationId) {
        return query("""
                        SELECT t.member_id, t.created_at, t.ical_polled_at, t.notification_polled_at
                        FROM user_feed_token t
                        JOIN station_member sm ON sm.id = t.member_id
                        WHERE sm.station_id = :station_id
                        ORDER BY t.created_at DESC;""")
                .single(call().bind("station_id", stationId))
                .map(FeedUse.map())
                .all();
    }

    public Optional<FeedToken> findByToken(String token) {
        return query("SELECT %s FROM user_feed_token WHERE token = :token;", USER_FEED_TOKEN_COLUMNS)
                .single(call().bind("token", token))
                .map(FeedToken.map())
                .first();
    }

    public FeedToken create(int memberId, String token) {
        return insertReturning(
                """
                INSERT INTO user_feed_token(member_id, token) VALUES(:member_id, :token)
                ON CONFLICT (member_id) DO UPDATE SET token = :token, created_at = now()
                RETURNING %s;""", call().bind("member_id", memberId).bind("token", token), FeedToken.map(), USER_FEED_TOKEN_COLUMNS);
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
