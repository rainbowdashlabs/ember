/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.InventoryFieldDefinition;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for per-inventory custom field definitions.
 */
@Singleton
public class InventoryFieldDefinitionRepository {

    private static final String COLUMNS = "id, inventory_id, key, label, field_type, required, sort_order, config";

    /**
     * Finds a field definition by id.
     */
    public Optional<InventoryFieldDefinition> findById(int id) {
        return query("""
                SELECT %s, config::text AS config
                FROM inventory_field_definition WHERE id = :id;""", COLUMNS)
                .single(call().bind("id", id))
                .map(InventoryFieldDefinition.map())
                .first();
    }

    /**
     * Returns every field defined for the given inventory, ordered for display.
     */
    public List<InventoryFieldDefinition> findByInventory(int inventoryId) {
        return query("""
                SELECT %s, config::text AS config
                FROM inventory_field_definition
                WHERE inventory_id = :inventory_id
                ORDER BY sort_order, key;""", COLUMNS)
                .single(call().bind("inventory_id", inventoryId))
                .map(InventoryFieldDefinition.map())
                .all();
    }

    /**
     * Creates a new field definition.
     */
    public InventoryFieldDefinition create(
            int inventoryId,
            String key,
            String label,
            FieldType fieldType,
            boolean required,
            int sortOrder,
            String configJson) {
        return query("""
                INSERT INTO inventory_field_definition(inventory_id, key, label, field_type, required, sort_order, config)
                VALUES(:inventory_id, :key, :label, :field_type, :required, :sort_order, :config::jsonb)
                RETURNING %s, config::text AS config;""", COLUMNS)
                .single(call().bind("inventory_id", inventoryId)
                        .bind("key", key)
                        .bind("label", label)
                        .bind("field_type", fieldType)
                        .bind("required", required)
                        .bind("sort_order", sortOrder)
                        .bind("config", configJson == null || configJson.isBlank() ? "{}" : configJson))
                .map(InventoryFieldDefinition.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates a field definition's mutable fields. The {@code field_type} and
     * {@code key} columns are intentionally not updatable here — changing
     * them goes through delete-and-re-add.
     */
    public boolean update(int id, String label, boolean required, int sortOrder, String configJson) {
        return query("""
                UPDATE inventory_field_definition
                SET label = :label, required = :required, sort_order = :sort_order, config = :config::jsonb
                WHERE id = :id;""")
                .single(call().bind("label", label)
                        .bind("required", required)
                        .bind("sort_order", sortOrder)
                        .bind("config", configJson == null || configJson.isBlank() ? "{}" : configJson)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a field definition by id. The orphaned key remains in any
     * item's metadata JSONB until the operator edits the item.
     */
    public boolean delete(int id) {
        return query("DELETE FROM inventory_field_definition WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Returns whether the inventory has any item that records a value for the
     * given field key. Used to gate field-type changes.
     */
    public boolean fieldHasAnyValue(int inventoryId, String key) {
        return query("""
                SELECT 1 FROM inventory_item
                WHERE inventory_id = :inventory_id
                  AND jsonb_exists(metadata -> 'fields', :key)
                LIMIT 1;""")
                .single(call().bind("inventory_id", inventoryId).bind("key", key))
                .map(row -> row.getInt(1))
                .first()
                .isPresent();
    }
}
