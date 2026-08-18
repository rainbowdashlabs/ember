/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.repository;

import de.chojo.sadu.mapper.wrapper.Row;
import dev.chojo.ember.feature.insights.entity.PageHitBucket;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Persists hourly page-hit aggregations. Counters accumulate via UPSERT on the
 * {@code (hour, page_id, country, referer_domain, is_bot)} primary key, so every flush
 * from the recorder lands as an atomic increment regardless of concurrent writes.
 *
 * <p>Read methods are intentionally narrow: a per-page leaderboard, hourly time series,
 * country breakdown, referrer breakdown, and totals. The insights routes compose these into
 * the responses the frontend renders; no business logic lives here.
 */
@Singleton
public class PageHitRepository {

    private static PageHitBucket mapBucket(Row row) throws SQLException {
        return new PageHitBucket(
                row.get("hour", INSTANT_TIMESTAMP),
                row.getInt("page_id"),
                row.getString("country"),
                row.getString("referer_domain"),
                row.getBoolean("is_bot"),
                row.getLong("hits"));
    }

    /**
     * Upserts a single hit bucket - adds the given hit count on top of the existing row if
     * one already exists for the {@code (hour, page_id, country, referer_domain, is_bot)}
     * key. Returns silently; SADU surfaces failures as exceptions.
     */
    public void upsert(PageHitBucket bucket) {
        query("""
                INSERT INTO page_hit_hourly(hour, page_id, country, referer_domain, is_bot, hits)
                VALUES (:hour, :page_id, :country, :referer_domain, :is_bot, :hits)
                ON CONFLICT (hour, page_id, country, referer_domain, is_bot) DO UPDATE
                SET hits = page_hit_hourly.hits + excluded.hits;""")
                .single(call().bind("hour", bucket.hour(), INSTANT_TIMESTAMP)
                        .bind("page_id", bucket.pageId())
                        .bind("country", bucket.country())
                        .bind("referer_domain", bucket.refererDomain())
                        .bind("is_bot", bucket.isBot())
                        .bind("hits", bucket.hits()))
                .insert();
    }

    /**
     * Returns hourly rows for the given window scoped to a single page. The caller is
     * responsible for filtering by bot flag when computing the dashboard headline.
     */
    public List<PageHitBucket> findForPage(int pageId, Instant from, Instant to) {
        return query("""
                SELECT hour, page_id, country, referer_domain, is_bot, hits
                FROM page_hit_hourly
                WHERE page_id = :page_id
                  AND hour >= :from
                  AND hour <= :to
                ORDER BY hour;""")
                .single(call().bind("page_id", pageId)
                        .bind("from", from, INSTANT_TIMESTAMP)
                        .bind("to", to, INSTANT_TIMESTAMP))
                .map(PageHitRepository::mapBucket)
                .all();
    }

    /**
     * Returns the per-page hit leaderboard for a station in the given window - one row per
     * page with summed hits and bot-hits. Sorted by total hits descending, capped by
     * {@code limit}.
     */
    public List<PageLeaderboardEntry> leaderboard(int stationId, Instant from, Instant to, int limit) {
        return query("""
                SELECT p.id AS page_id,
                       p.title AS title,
                       p.slug AS slug,
                       coalesce(sum(h.hits) FILTER (WHERE h.is_bot = FALSE), 0) AS hits,
                       coalesce(sum(h.hits) FILTER (WHERE h.is_bot = TRUE), 0)  AS bot_hits
                FROM station_page p
                LEFT JOIN page_hit_hourly h
                       ON h.page_id = p.id
                      AND h.hour >= :from
                      AND h.hour <= :to
                WHERE p.station_id = :station_id
                GROUP BY p.id, p.title, p.slug
                ORDER BY hits DESC, p.id
                LIMIT :limit;""")
                .single(call().bind("station_id", stationId)
                        .bind("from", from, INSTANT_TIMESTAMP)
                        .bind("to", to, INSTANT_TIMESTAMP)
                        .bind("limit", limit))
                .map(row -> new PageLeaderboardEntry(
                        row.getInt("page_id"),
                        row.getString("title"),
                        row.getString("slug"),
                        row.getLong("hits"),
                        row.getLong("bot_hits")))
                .all();
    }

    /**
     * Returns hits-per-country totals for one page in the given window. Excludes bot hits.
     */
    public List<DimensionTotal> countryTotalsForPage(int pageId, Instant from, Instant to) {
        return query("""
                SELECT country AS dim, sum(hits) AS hits
                FROM page_hit_hourly
                WHERE page_id = :page_id
                  AND hour >= :from
                  AND hour <= :to
                  AND is_bot = FALSE
                GROUP BY country
                ORDER BY hits DESC, country;""")
                .single(call().bind("page_id", pageId)
                        .bind("from", from, INSTANT_TIMESTAMP)
                        .bind("to", to, INSTANT_TIMESTAMP))
                .map(row -> new DimensionTotal(row.getString("dim"), row.getLong("hits")))
                .all();
    }

    /**
     * Returns hits-per-referer-domain totals for one page in the given window. Excludes bot
     * hits - the dashboard already ships bot referrers behind its own toggle.
     */
    public List<DimensionTotal> refererTotalsForPage(int pageId, Instant from, Instant to) {
        return query("""
                SELECT referer_domain AS dim, sum(hits) AS hits
                FROM page_hit_hourly
                WHERE page_id = :page_id
                  AND hour >= :from
                  AND hour <= :to
                  AND is_bot = FALSE
                GROUP BY referer_domain
                ORDER BY hits DESC, referer_domain;""")
                .single(call().bind("page_id", pageId)
                        .bind("from", from, INSTANT_TIMESTAMP)
                        .bind("to", to, INSTANT_TIMESTAMP))
                .map(row -> new DimensionTotal(row.getString("dim"), row.getLong("hits")))
                .all();
    }

    /**
     * Returns total hits across a station's pages bucketed by hour, optionally including
     * bots. Used by the headline chart on the insights view.
     */
    public List<HourlyTotal> stationHourlyTotals(int stationId, Instant from, Instant to, boolean includeBots) {
        var where = WhereBuilder.create().addIf(!includeBots, "AND h.is_bot = FALSE");
        return query("""
                SELECT h.hour AS hour, sum(h.hits) AS hits
                FROM page_hit_hourly h
                JOIN station_page p ON p.id = h.page_id
                WHERE p.station_id = :station_id
                  AND h.hour >= :from
                  AND h.hour <= :to
                  %s
                GROUP BY h.hour
                ORDER BY h.hour;""", where.fragment())
                .single(where.apply(call().bind("station_id", stationId)
                        .bind("from", from, INSTANT_TIMESTAMP)
                        .bind("to", to, INSTANT_TIMESTAMP)))
                .map(row -> new HourlyTotal(row.get("hour", INSTANT_TIMESTAMP), row.getLong("hits")))
                .all();
    }

    /**
     * Returns the number of times a given referer_domain has appeared for a page in the
     * recent window. The recorder uses this to decide whether a long-tail domain should be
     * collapsed to {@code other} on insert.
     */
    public long recentRefererCount(int pageId, String refererDomain, Instant since) {
        return query("""
                SELECT coalesce(sum(hits), 0) AS total
                FROM page_hit_hourly
                WHERE page_id = :page_id
                  AND referer_domain = :referer_domain
                  AND hour >= :since;""")
                .single(call().bind("page_id", pageId)
                        .bind("referer_domain", refererDomain)
                        .bind("since", since, INSTANT_TIMESTAMP))
                .map(row -> row.getLong("total"))
                .first()
                .orElse(0L);
    }

    /**
     * Drops every row whose bucket hour is older than the given cutoff. Returns the number
     * of pruned rows for logging.
     */
    public int pruneBefore(Instant cutoff) {
        return query("""
                DELETE FROM page_hit_hourly
                WHERE hour < :cutoff;""")
                .single(call().bind("cutoff", cutoff, INSTANT_TIMESTAMP))
                .delete()
                .rows();
    }

    /**
     * One row of the per-page leaderboard.
     *
     * @param pageId  internal page id
     * @param title   page title
     * @param slug    page slug
     * @param hits    sum of non-bot hits in the queried window
     * @param botHits sum of bot hits in the queried window
     */
    public record PageLeaderboardEntry(int pageId, String title, String slug, long hits, long botHits) {}

    /**
     * One row of a dimension breakdown (country, referer, …) for the drill-down view.
     */
    public record DimensionTotal(String dimension, long hits) {}

    /**
     * One bucketed hourly total for a station's combined pages.
     */
    public record HourlyTotal(Instant hour, long hits) {}
}
