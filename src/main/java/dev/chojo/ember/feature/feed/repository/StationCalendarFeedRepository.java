/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.feed.entity.StationCalendarFeed;
import dev.chojo.ember.feature.feed.entity.StationFeedLevel;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the {@code station_calendar_feed} table and the categories each feed leaves out.
 *
 * <p>Every lookup by id is scoped to a station, so a feed id of another station reads as absent.
 */
@Singleton
public class StationCalendarFeedRepository {

    private static final String COLUMNS = """
            f.id, f.station_id, f.name, f.token, f.level, f.created_by, f.created_at, f.polled_at,
            ARRAY(SELECT h.category_id
                  FROM station_calendar_feed_hidden_category h
                  WHERE h.feed_id = f.id
                  ORDER BY h.category_id) AS hidden_category_ids""";

    /**
     * Every calendar feed of a station, oldest first.
     *
     * @param stationId the station
     * @return its feeds
     */
    public List<StationCalendarFeed> findByStation(int stationId) {
        return query("SELECT %s FROM station_calendar_feed f WHERE f.station_id = :station_id ORDER BY f.id;", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationCalendarFeed.map())
                .all();
    }

    /**
     * One calendar feed of a station.
     *
     * @param id        the feed
     * @param stationId the station it has to belong to
     * @return the feed, empty when it is gone or belongs to another station
     */
    public Optional<StationCalendarFeed> findInStation(int id, int stationId) {
        return query("SELECT %s FROM station_calendar_feed f WHERE f.id = :id AND f.station_id = :station_id;", COLUMNS)
                .single(call().bind("id", id).bind("station_id", stationId))
                .map(StationCalendarFeed.map())
                .first();
    }

    /**
     * The calendar feed behind a feed address.
     *
     * @param token the token of the address
     * @return the feed, empty when no feed has that token
     */
    public Optional<StationCalendarFeed> findByToken(String token) {
        return query("SELECT %s FROM station_calendar_feed f WHERE f.token = :token;", COLUMNS)
                .single(call().bind("token", token))
                .map(StationCalendarFeed.map())
                .first();
    }

    /**
     * Creates a calendar feed that leaves no category out.
     *
     * @param stationId the station
     * @param name      the feed's name
     * @param level     up to which visibility it shows appointments
     * @param token     the token of its address
     * @param createdBy the member creating it
     * @return the id of the new feed
     */
    public int create(int stationId, String name, StationFeedLevel level, String token, int createdBy) {
        return query("""
                        INSERT INTO station_calendar_feed(station_id, name, level, token, created_by)
                        VALUES (:station_id, :name, :level, :token, :created_by)
                        RETURNING id;""")
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("level", level.name())
                        .bind("token", token)
                        .bind("created_by", createdBy))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    /**
     * Changes the name and level of a calendar feed.
     *
     * @return whether the feed was there to change
     */
    public boolean update(int id, int stationId, String name, StationFeedLevel level) {
        return query("""
                        UPDATE station_calendar_feed SET name = :name, level = :level
                        WHERE id = :id AND station_id = :station_id;""")
                .single(call().bind("id", id)
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("level", level.name()))
                .update()
                .changed();
    }

    /**
     * Gives a calendar feed a new token, which ends its old address.
     *
     * @return whether the feed was there to change
     */
    public boolean replaceToken(int id, int stationId, String token) {
        return query("UPDATE station_calendar_feed SET token = :token WHERE id = :id AND station_id = :station_id;")
                .single(call().bind("id", id).bind("station_id", stationId).bind("token", token))
                .update()
                .changed();
    }

    /**
     * Replaces the categories a calendar feed leaves out. Ids that are not categories of the feed's
     * station are skipped; the caller checks them before.
     *
     * @param id          the feed
     * @param stationId   the station it belongs to
     * @param categoryIds the categories it leaves out from now on
     */
    public void replaceHiddenCategories(int id, int stationId, List<Integer> categoryIds) {
        Transactions.run(() -> {
            query("DELETE FROM station_calendar_feed_hidden_category WHERE feed_id = :id;")
                    .single(call().bind("id", id))
                    .delete();
            if (categoryIds.isEmpty()) return;
            query("""
                            INSERT INTO station_calendar_feed_hidden_category(feed_id, category_id)
                            SELECT f.id, c.id
                            FROM station_calendar_feed f
                            JOIN event_category c ON c.station_id = f.station_id
                            WHERE f.id = :id AND f.station_id = :station_id AND c.id = ANY(:category_ids::INT[])
                            ON CONFLICT DO NOTHING;""")
                    .single(call().bind("id", id)
                            .bind("station_id", stationId)
                            .bind("category_ids", categoryIds, PostgreSqlTypes.INTEGER))
                    .insert();
        });
    }

    /**
     * Records that a calendar fetched the feed.
     *
     * @param id the feed
     */
    public void recordPoll(int id) {
        query("UPDATE station_calendar_feed SET polled_at = now() WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Deletes a calendar feed, which ends its address.
     *
     * @return whether the feed was there to delete
     */
    public boolean delete(int id, int stationId) {
        return query("DELETE FROM station_calendar_feed WHERE id = :id AND station_id = :station_id;")
                .single(call().bind("id", id).bind("station_id", stationId))
                .delete()
                .changed();
    }
}
