/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

/**
 * Cross-cutting configuration for persisted metrics. Owns the retention windows for every
 * metric table the application writes — operators can shrink them to save disk space on
 * constrained deployments, or expand them when long-running trend analysis matters more.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("METRICS")
public class Metrics {

    /**
     * How many days of {@code api_request_log} rows to keep. Each row is one API request, so
     * this table grows linearly with traffic — keep low (default 3) unless you actually need
     * historical per-request data.
     */
    @Overwrite(env = @Env)
    private int requestStatsRetentionDays = 3;

    /**
     * How many days of {@code feed_metric_daily} and inactive {@code feed_user_agent_stat}
     * rows to keep. Feed tables are pre-aggregated per day, so retention can comfortably be
     * much longer than the per-request log.
     */
    @Overwrite(env = @Env)
    private int feedStatsRetentionDays = 90;

    /**
     * Whether to record per-station traffic counters into {@code station_traffic_hourly}.
     * Defaults to on; switch off only for benchmark / very constrained deployments where
     * the in-memory accumulator and ~30 s flush cycle is unwelcome overhead.
     */
    @Overwrite(env = @Env)
    private boolean trafficEnabled = true;

    /**
     * How many days of pre-aggregated {@code station_traffic_hourly} rows to keep. The
     * table is tiny (hours × stations × 3 auth buckets) so a quarter of visibility is the
     * default; expand for long-term trend analysis.
     */
    @Overwrite(env = @Env)
    private int trafficRetentionDays = 90;

    /**
     * Cadence at which the {@code StationTrafficRecorder} flushes its in-memory bucket map
     * to the database via UPSERT. Shorter intervals reduce data loss on a crash but raise
     * the number of write operations; the default of 30 s matches the concept document.
     */
    @Overwrite(env = @Env)
    private int trafficFlushIntervalSeconds = 30;

    public int requestStatsRetentionDays() {
        return requestStatsRetentionDays;
    }

    public int feedStatsRetentionDays() {
        return feedStatsRetentionDays;
    }

    public boolean trafficEnabled() {
        return trafficEnabled;
    }

    public int trafficRetentionDays() {
        return trafficRetentionDays;
    }

    public int trafficFlushIntervalSeconds() {
        return trafficFlushIntervalSeconds;
    }
}
