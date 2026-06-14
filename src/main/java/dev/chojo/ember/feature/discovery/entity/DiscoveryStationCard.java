/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
        Instant publishedAt) {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public JsonNode toJson() {
        return MAPPER.valueToTree(this);
    }

    public static DiscoveryStationCard parse(JsonNode node) {
        return MAPPER.convertValue(node, DiscoveryStationCard.class);
    }

    public static DiscoveryStationCard parse(String json) {
        try {
            return MAPPER.readValue(json, DiscoveryStationCard.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DiscoveryStationCard", e);
        }
    }

    public String toJsonString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DiscoveryStationCard", e);
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
}
