/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared JSON utilities for parsing and serializing JSON nodes.
 */
public final class JsonUtil {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private JsonUtil() {}

    /**
     * Parses a JSON string into a {@link JsonNode}. Returns an empty object node on null/blank/error.
     */
    public static JsonNode parseNode(String json) {
        if (json == null || json.isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    /**
     * Serializes a {@link JsonNode} to a JSON string. Returns "{}" on null.
     */
    public static String toJson(JsonNode node) {
        if (node == null) return "{}";
        return node.toString();
    }
}
