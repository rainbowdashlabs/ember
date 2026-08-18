/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.repository;

import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.entity.TrafficBucket;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Persists hourly traffic aggregations. Two upsert paths because {@code station_id IS NULL}
 * rows need their own conflict target - the partial unique indexes on
 * {@code station_traffic_hourly} treat per-station and instance-global buckets as separate
 * uniqueness domains, and PostgreSQL needs an explicit predicate or column list that matches
 * exactly one of them per insert.
 */
@Singleton
public class StationTrafficRepository {

    /**
     * Upserts a single hourly bucket - adds the given byte / request totals on top of the
     * existing row if one already exists for the {@code (hour, stationId, auth)} key. Returns
     * silently; failures are surfaced by SADU as exceptions.
     */
    public void upsert(TrafficBucket bucket) {
        if (bucket.stationId() == null) {
            upsertGlobal(bucket);
        } else {
            upsertStation(bucket);
        }
    }

    /**
     * Returns hourly rows for the given window, optionally filtered by station and auth
     * bucket. {@code stationId == null} returns rows for every station (use
     * {@link #findGlobal} for instance-global rows). {@code auth == null} returns all three
     * buckets.
     */
    public List<TrafficBucket> findHourly(Instant from, Instant to, Integer stationId, AuthBucket auth) {
        var where = WhereBuilder.create()
                .add("AND station_id = :station_id", "station_id", stationId)
                .add("AND auth = :auth", "auth", auth);
        return query("""
                SELECT hour, station_id, auth, ingress_bytes, egress_bytes, requests
                FROM station_traffic_hourly
                WHERE hour >= :from
                  AND hour <= :to
                  %s
                ORDER BY hour, auth;""", where.fragment())
                .single(where.apply(call().bind("from", from, INSTANT_TIMESTAMP).bind("to", to, INSTANT_TIMESTAMP)))
                .map(row -> new TrafficBucket(
                        row.get("hour", INSTANT_TIMESTAMP),
                        row.getObject("station_id") == null ? null : row.getInt("station_id"),
                        row.getEnum("auth", AuthBucket.class),
                        row.getLong("ingress_bytes"),
                        row.getLong("egress_bytes"),
                        row.getLong("requests")))
                .all();
    }

    /**
     * Returns the instance-global rows ({@code station_id IS NULL}) for the given window.
     * Separate method so callers don't accidentally aggregate per-station and global rows
     * together when summing across the whole table.
     */
    public List<TrafficBucket> findGlobal(Instant from, Instant to, AuthBucket auth) {
        var where = WhereBuilder.create().add("AND auth = :auth", "auth", auth);
        return query("""
                SELECT hour, station_id, auth, ingress_bytes, egress_bytes, requests
                FROM station_traffic_hourly
                WHERE station_id IS NULL
                  AND hour >= :from
                  AND hour <= :to
                  %s
                ORDER BY hour, auth;""", where.fragment())
                .single(where.apply(call().bind("from", from, INSTANT_TIMESTAMP).bind("to", to, INSTANT_TIMESTAMP)))
                .map(row -> new TrafficBucket(
                        row.get("hour", INSTANT_TIMESTAMP),
                        null,
                        row.getEnum("auth", AuthBucket.class),
                        row.getLong("ingress_bytes"),
                        row.getLong("egress_bytes"),
                        row.getLong("requests")))
                .all();
    }

    /**
     * Drops every row whose bucket hour is older than the given cutoff. Returns the number
     * of pruned rows for logging.
     */
    public int pruneBefore(Instant cutoff) {
        return query("""
                DELETE FROM station_traffic_hourly
                WHERE hour < :cutoff;""")
                .single(call().bind("cutoff", cutoff, INSTANT_TIMESTAMP))
                .delete()
                .rows();
    }

    private void upsertStation(TrafficBucket bucket) {
        query("""
                INSERT INTO station_traffic_hourly(hour, station_id, auth, ingress_bytes, egress_bytes, requests)
                VALUES (:hour, :station_id, :auth, :ingress, :egress, :requests)
                ON CONFLICT (hour, station_id, auth) WHERE station_id IS NOT NULL DO UPDATE
                SET ingress_bytes = station_traffic_hourly.ingress_bytes + excluded.ingress_bytes,
                    egress_bytes  = station_traffic_hourly.egress_bytes  + excluded.egress_bytes,
                    requests      = station_traffic_hourly.requests      + excluded.requests;""")
                .single(call().bind("hour", bucket.hour(), INSTANT_TIMESTAMP)
                        .bind("station_id", bucket.stationId())
                        .bind("auth", bucket.auth().name())
                        .bind("ingress", bucket.ingressBytes())
                        .bind("egress", bucket.egressBytes())
                        .bind("requests", bucket.requests()))
                .insert();
    }

    private void upsertGlobal(TrafficBucket bucket) {
        query("""
                INSERT INTO station_traffic_hourly(hour, station_id, auth, ingress_bytes, egress_bytes, requests)
                VALUES (:hour, NULL, :auth, :ingress, :egress, :requests)
                ON CONFLICT (hour, auth) WHERE station_id IS NULL DO UPDATE
                SET ingress_bytes = station_traffic_hourly.ingress_bytes + excluded.ingress_bytes,
                    egress_bytes  = station_traffic_hourly.egress_bytes  + excluded.egress_bytes,
                    requests      = station_traffic_hourly.requests      + excluded.requests;""")
                .single(call().bind("hour", bucket.hour(), INSTANT_TIMESTAMP)
                        .bind("auth", bucket.auth().name())
                        .bind("ingress", bucket.ingressBytes())
                        .bind("egress", bucket.egressBytes())
                        .bind("requests", bucket.requests()))
                .insert();
    }
}
