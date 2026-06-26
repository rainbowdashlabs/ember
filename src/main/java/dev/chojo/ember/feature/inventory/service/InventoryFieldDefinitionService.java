/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.FieldConfig;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.InventoryFieldDefinition;
import dev.chojo.ember.feature.inventory.repository.InventoryFieldDefinitionRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service for managing per-inventory custom field definitions. Owns the
 * shape-stability rules described in the storage concept doc: keys never
 * change, field types never change after the first value is recorded, and
 * required is enforced only on create and on the next edit.
 */
@Singleton
public class InventoryFieldDefinitionService {
    private static final Logger log = LoggerFactory.getLogger(InventoryFieldDefinitionService.class);

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final InventoryFieldDefinitionRepository repository;

    @Inject
    public InventoryFieldDefinitionService(InventoryFieldDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns every field definition for the given inventory, ordered for display.
     */
    public List<InventoryFieldDefinition> findByInventory(int inventoryId) {
        return repository.findByInventory(inventoryId);
    }

    /**
     * Finds one field definition by id.
     */
    public Optional<InventoryFieldDefinition> findById(int id) {
        return repository.findById(id);
    }

    /**
     * Creates a new field definition for an inventory. Validates that the key
     * is a stable machine identifier, the config matches the field type, and
     * no other field on the inventory uses the same key.
     */
    public InventoryFieldDefinition create(
            int inventoryId,
            String key,
            String label,
            FieldType fieldType,
            boolean required,
            int sortOrder,
            FieldConfig config) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Field key must be lower-case ASCII, start with a letter, and use only [a-z0-9_]");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Field label is required");
        }
        if (fieldType == null) {
            throw new IllegalArgumentException("Field type is required");
        }
        FieldConfig effectiveConfig = config != null ? config : defaultConfig(fieldType);
        if (effectiveConfig.fieldType() != fieldType) {
            throw new IllegalArgumentException("Field config type does not match field type");
        }
        for (InventoryFieldDefinition existing : repository.findByInventory(inventoryId)) {
            if (existing.key().equals(key)) {
                throw new IllegalArgumentException("Field key already exists on this inventory");
            }
        }
        InventoryFieldDefinition created =
                repository.create(inventoryId, key, label, fieldType, required, sortOrder, effectiveConfig.toJson());
        log.info(
                "Created field definition {} (key='{}', label='{}', type={}, required={}) in inventory {}",
                created.id(),
                key,
                label,
                fieldType,
                required,
                inventoryId);
        return created;
    }

    /**
     * Updates the mutable parts of a field definition: label, required flag,
     * sort order and (matching-type) config. Changing the field type or key
     * is intentionally rejected — the operator deletes and re-adds instead.
     */
    public Optional<InventoryFieldDefinition> update(
            int id, String label, boolean required, int sortOrder, FieldConfig config) {
        Optional<InventoryFieldDefinition> existing = repository.findById(id);
        if (existing.isEmpty()) {
            log.warn("Update skipped: field definition {} not found", id);
            return Optional.empty();
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Field label is required");
        }
        FieldConfig effective =
                config != null ? config : defaultConfig(existing.get().fieldType());
        if (effective.fieldType() != existing.get().fieldType()) {
            throw new IllegalArgumentException("Field config type does not match the field's type");
        }
        if (!repository.update(id, label, required, sortOrder, effective.toJson())) {
            log.warn("Update of field definition {} did not change any row", id);
            return Optional.empty();
        }
        log.info("Updated field definition {} (label='{}', required={}, sortOrder={})", id, label, required, sortOrder);
        return repository.findById(id);
    }

    /**
     * Deletes a field definition. The orphaned key remains in any item
     * metadata JSON until the operator edits the item.
     */
    public boolean delete(int id) {
        boolean deleted = repository.delete(id);
        if (deleted) log.info("Deleted field definition {}", id);
        else log.warn("Delete of field definition {} did not change any row", id);
        return deleted;
    }

    /**
     * Returns whether any item in the inventory carries a recorded value for
     * the given field key. Used by callers that gate destructive changes.
     */
    public boolean fieldHasAnyValue(int inventoryId, String key) {
        return repository.fieldHasAnyValue(inventoryId, key);
    }

    /**
     * Returns the default-shaped config for the given field type.
     */
    public FieldConfig defaultConfig(FieldType type) {
        return switch (type) {
            case DATE -> new FieldConfig.DateConfig();
            case ENUM -> new FieldConfig.EnumConfig(List.of());
            case TEXT -> new FieldConfig.TextConfig(false, 200);
            case NUMBER -> new FieldConfig.NumberConfig(null, null, null, "");
            case BOOLEAN -> new FieldConfig.BooleanConfig("Yes", "No");
        };
    }
}
