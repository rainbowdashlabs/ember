/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.entity;

import java.time.Instant;

/**
 * One hourly aggregation bucket. {@code stationId} is {@code null} for instance-global rows
 * (admin endpoints, instance config, static assets). Used both as the upsert payload from
 * the recorder and as the read row when listing hourly data.
 */
public record TrafficBucket(
        Instant hour, Integer stationId, AuthBucket auth, long ingressBytes, long egressBytes, long requests) {}
