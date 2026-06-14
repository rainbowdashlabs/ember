/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Structured metadata associated with an inventory item, persisted as JSONB.
 *
 * @param owned whether the item is personally owned by the assigned member (for MIXED inventories)
 */
public record InventoryItemMetadata(boolean owned) {
    private static final Logger log = getLogger(InventoryItemMetadata.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();
    private static final InventoryItemMetadata EMPTY = new InventoryItemMetadata(false);

    public static InventoryItemMetadata empty() {
        return EMPTY;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Parses a JSON string into an {@link InventoryItemMetadata}, returning a default empty value on failure.
     *
     * @param json the JSON metadata string, may be null or blank
     * @return the parsed metadata or a default empty value
     */
    public static InventoryItemMetadata parse(String json) {
        if (json == null || json.isBlank()) return EMPTY;
        try {
            return MAPPER.readValue(json, InventoryItemMetadata.class);
        } catch (Exception e) {
            log.error("Failed to parse inventory item metadata: {}", json, e);
            return EMPTY;
        }
    }
}
