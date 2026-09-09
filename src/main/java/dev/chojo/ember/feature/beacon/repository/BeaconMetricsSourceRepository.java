/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.repository;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What this instance holds, counted for the daily report.
 *
 * <p>Exact numbers here, bucketed on the way out. Nothing in this class reads a name, an address or
 * a federation identity: the only thing that leaves a station is its metrics identifier and how much
 * it has.
 */
@Singleton
public class BeaconMetricsSourceRepository {

    /**
     * One station's numbers, under the name it goes by in the metrics and no other.
     *
     * @param metricsUid the station's metrics identifier
     * @param members    how many members it has, former ones excluded
     * @param inventory  how many inventory items it holds
     */
    public record StationCounts(UUID metricsUid, int members, int inventory) {}

    /**
     * Every station's numbers in one query, because the daily report goes as one batch.
     *
     * @return one entry per station
     */
    public List<StationCounts> stationCounts() {
        return query("""
                        SELECT s.metrics_uid,
                               count(DISTINCT sm.id) FILTER (WHERE sm.former = FALSE) AS members,
                               count(DISTINCT ii.id)                                  AS inventory
                        FROM station s
                                 LEFT JOIN station_member sm ON sm.station_id = s.id
                                 LEFT JOIN inventory i ON i.station_id = s.id
                                 LEFT JOIN inventory_item ii ON ii.inventory_id = i.id
                        GROUP BY s.metrics_uid;""")
                .single()
                .map(row -> new StationCounts(
                        UUID.fromString(row.getString("metrics_uid")), row.getInt("members"), row.getInt("inventory")))
                .all();
    }

    /** How many accounts the instance holds. */
    public int accountCount() {
        return query("SELECT count(*) AS c FROM account;")
                .single()
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
    }

    /** How many stations the instance holds. */
    public int stationCount() {
        return query("SELECT count(*) AS c FROM station;")
                .single()
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
    }

    /** How many inventory items the instance holds across every station. */
    public int inventoryCount() {
        return query("SELECT count(*) AS c FROM inventory_item;")
                .single()
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
    }

    /** How many members the instance holds across every station, former ones excluded. */
    public int memberCount() {
        return query("SELECT count(*) AS c FROM station_member WHERE former = FALSE;")
                .single()
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
    }
}
