/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

import dev.chojo.ember.util.Json;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One station card as exposed by the {@code /public/discovery/stations} endpoint and stored
 * in the local cache.
 *
 * <p>Member counts are bucketed to avoid leaking precise size of small stations.
 */
public record DiscoveryStationCard(
        String stationUid,
        String name,
        String slogan,
        String logoUrl,
        String country,
        String region,
        String city,
        String contactUrl,
        List<String> tags,
        String memberCount,
        Instant publishedAt,
        String addressLine,
        BigDecimal latitude,
        BigDecimal longitude,
        String clusterUid,
        String clusterName) {

    /**
     * A card from a peer that predates the cluster fields, which reads as "not in a cluster".
     *
     * <p>Older instances send nothing for them, and absent is exactly what a station outside any cluster
     * would send anyway, so no version check is needed to tell the two apart.
     */
    public DiscoveryStationCard(
            String stationUid,
            String name,
            String slogan,
            String logoUrl,
            String country,
            String region,
            String city,
            String contactUrl,
            List<String> tags,
            String memberCount,
            Instant publishedAt,
            String addressLine,
            BigDecimal latitude,
            BigDecimal longitude) {
        this(
                stationUid,
                name,
                slogan,
                logoUrl,
                country,
                region,
                city,
                contactUrl,
                tags,
                memberCount,
                publishedAt,
                addressLine,
                latitude,
                longitude,
                null,
                null);
    }

    public static DiscoveryStationCard parse(String json) {
        try {
            return Json.MAPPER.readValue(json, DiscoveryStationCard.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DiscoveryStationCard", e);
        }
    }

    /**
     * Bucketing function used by the public station endpoint when projecting a real member
     * count down to one of {@code <10 | 10-50 | 50-200 | 200+}.
     */
    public static String bucketMemberCount(int actual) {
        if (actual < 10) return "<10";
        if (actual < 50) return "10-50";
        if (actual < 200) return "50-200";
        return "200+";
    }

    public JsonNode toJson() {
        return Json.MAPPER.valueToTree(this);
    }

    public String toJsonString() {
        try {
            return Json.MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DiscoveryStationCard", e);
        }
    }
}
