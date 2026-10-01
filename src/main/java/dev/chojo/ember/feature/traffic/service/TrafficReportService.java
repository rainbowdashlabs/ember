/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.service;

import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.entity.TrafficBucket;
import dev.chojo.ember.feature.traffic.repository.StationTrafficRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The traffic the instance served, hour by hour, for the monitoring charts.
 *
 * <p>The rows are handed out in the shape they are counted in: the charts do their own grouping and
 * stacking, depending on what they draw.
 */
@Singleton
public class TrafficReportService {
    private final StationTrafficRepository traffic;

    @Inject
    public TrafficReportService(StationTrafficRepository traffic) {
        this.traffic = traffic;
    }

    /**
     * The hourly rows within a stretch of time.
     *
     * @param stationId only this station's traffic, or null for all of it
     * @param auth      only this kind of request, or null for every kind
     */
    public HourlyTrafficResponse hourly(
            Instant from, Instant to, @Nullable Integer stationId, @Nullable AuthBucket auth) {
        return new HourlyTrafficResponse(traffic.findHourly(from, to, stationId, auth).stream()
                .map(HourlyTrafficRow::from)
                .toList());
    }

    /**
     * One hour of one station's traffic of one kind.
     */
    public record HourlyTrafficRow(
            Instant hour,
            @Nullable Integer stationId,
            AuthBucket auth,
            long ingressBytes,
            long egressBytes,
            long requests) {
        static HourlyTrafficRow from(TrafficBucket b) {
            return new HourlyTrafficRow(
                    b.hour(), b.stationId(), b.auth(), b.ingressBytes(), b.egressBytes(), b.requests());
        }
    }

    /**
     * The rows, in a container so totals can be added beside them without changing their shape.
     */
    public record HourlyTrafficResponse(List<HourlyTrafficRow> rows) {}
}
