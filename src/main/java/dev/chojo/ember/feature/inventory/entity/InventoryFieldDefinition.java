/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Per-inventory custom field schema entry. Items in the inventory carry one
 * JSONB sub-object keyed by {@code key} inside {@code inventory_item.metadata.fields}.
 *
 * @param id          the unique field-definition identifier
 * @param inventoryId the inventory this field belongs to
 * @param key         stable machine identifier used in API responses and exports
 * @param label       display label shown in forms and list columns
 * @param fieldType   value kind ({@link FieldType})
 * @param required    whether the value must be present on create and on next edit
 * @param sortOrder   ordering hint for form layout and column order
 * @param config      typed configuration for the field type
 */
public record InventoryFieldDefinition(
        int id,
        int inventoryId,
        String key,
        String label,
        FieldType fieldType,
        boolean required,
        int sortOrder,
        FieldConfig config) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryFieldDefinition> map() {
        return row -> {
            FieldType type = row.getEnum("field_type", FieldType.class);
            return new InventoryFieldDefinition(
                    row.getInt("id"),
                    row.getInt("inventory_id"),
                    row.getString("key"),
                    row.getString("label"),
                    type,
                    row.getBoolean("required"),
                    row.getInt("sort_order"),
                    FieldConfig.parse(type, row.getString("config")));
        };
    }
}
