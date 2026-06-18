/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.entity;

import java.time.Instant;

/**
 * One hourly hit-counter bucket for a public {@code station_page}. Used both as the upsert
 * payload from {@code PageHitRecorder} and as the read row when querying the table. No IPs
 * or visitor identifiers are tracked — see concept §7.1.
 */
public record PageHitBucket(Instant hour, int pageId, String country, String refererDomain, boolean isBot, long hits) {}
