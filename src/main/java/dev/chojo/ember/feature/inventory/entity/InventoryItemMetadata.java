/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Structured metadata associated with an inventory item, persisted as JSONB.
 *
 * @param owned  whether the item is personally owned by the assigned member (for MIXED inventories)
 * @param fields per-inventory custom field values keyed by field key; never {@code null}
 */
public record InventoryItemMetadata(boolean owned, ItemFieldValues fields) {
    private static final Logger log = getLogger(InventoryItemMetadata.class);
    private static final ObjectMapper MAPPER = Json.CONFIG_MAPPER;
    private static final InventoryItemMetadata EMPTY = new InventoryItemMetadata(false, ItemFieldValues.empty());

    /**
     * Normalises a {@code null} field-values container to {@link ItemFieldValues#empty()}.
     */
    public InventoryItemMetadata {
        fields = fields == null ? ItemFieldValues.empty() : fields;
    }

    /**
     * Backwards-compatible single-argument constructor for the {@code owned} flag.
     */
    public InventoryItemMetadata(boolean owned) {
        this(owned, ItemFieldValues.empty());
    }

    public static InventoryItemMetadata empty() {
        return EMPTY;
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

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }
}
