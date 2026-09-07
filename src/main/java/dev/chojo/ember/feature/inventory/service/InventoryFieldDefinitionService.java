/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.FieldConfig;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.InventoryFieldDefinition;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryFieldDefinitionRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final InventoryArtRepository artRepository;
    private final InventoryRepository inventoryRepository;

    @Inject
    public InventoryFieldDefinitionService(
            InventoryFieldDefinitionRepository repository,
            InventoryArtRepository artRepository,
            InventoryRepository inventoryRepository) {
        this.repository = repository;
        this.artRepository = artRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Returns every field definition anywhere in the given inventory, at all three levels, ordered
     * for display.
     */
    public List<InventoryFieldDefinition> findByInventory(int inventoryId) {
        return repository.findByInventory(inventoryId);
    }

    /**
     * The fields defined for the whole inventory and nothing narrower.
     */
    public List<InventoryFieldDefinition> findInventoryLevel(int inventoryId) {
        return repository.findInventoryLevel(inventoryId);
    }

    /**
     * The fields defined for one kind of thing.
     */
    public List<InventoryFieldDefinition> findByArt(int artId) {
        return repository.findByArt(artId);
    }

    /**
     * The fields defined for one single piece.
     */
    public List<InventoryFieldDefinition> findByItem(int itemId) {
        return repository.findByItem(itemId);
    }

    /**
     * Finds one field definition by id.
     */
    public Optional<InventoryFieldDefinition> findById(int id) {
        return repository.findById(id);
    }

    /**
     * The fields that describe one piece, with the collision rule applied.
     *
     * <p>Values live in one map keyed by the field key, so the same key defined at two levels would
     * otherwise describe one value twice. The narrowest definition wins: what somebody wrote about
     * this piece beats what they wrote about its kind, and that beats what they wrote about the
     * whole drawer. The wider definition is not deleted and comes back into force the moment the
     * narrow one goes.
     *
     * <p>A piece with no kind simply gets the inventory's fields and its own, which is why a piece
     * that never had a kind needs no special case anywhere.
     *
     * <p>What is <em>not</em> here is the other half of the rule: a value whose describing field has
     * gone, because the kind was removed or changed, is kept on the piece and stops being shown.
     * Nothing in this list mentions it and nothing deletes it, so it reads again the day a kind of
     * that name comes back.
     *
     * @param item the piece
     * @return its fields, in display order, one entry per key
     */
    public List<InventoryFieldDefinition> resolveForItem(InventoryItem item) {
        Map<String, InventoryFieldDefinition> byKey = new LinkedHashMap<>();
        List<InventoryFieldDefinition> levels = new ArrayList<>(repository.findInventoryLevel(item.inventoryId()));
        if (item.artId() != null) levels.addAll(repository.findByArt(item.artId()));
        levels.addAll(repository.findByItem(item.id()));
        for (InventoryFieldDefinition definition : levels) {
            InventoryFieldDefinition standing = byKey.get(definition.key());
            if (standing == null || definition.level().compareTo(standing.level()) >= 0) {
                byKey.put(definition.key(), definition);
            }
        }
        return byKey.values().stream()
                .sorted(Comparator.comparingInt(InventoryFieldDefinition::sortOrder)
                        .thenComparing(InventoryFieldDefinition::key))
                .toList();
    }

    /**
     * Creates a new field definition for a whole inventory.
     */
    public InventoryFieldDefinition create(
            int inventoryId,
            String key,
            String label,
            FieldType fieldType,
            boolean required,
            int sortOrder,
            FieldConfig config) {
        return create(inventoryId, null, null, key, label, fieldType, required, sortOrder, config);
    }

    /**
     * Creates a new field definition at one of the three levels. Validates that the key is a stable
     * machine identifier, that the config matches the field type, that at most one of the two narrow
     * levels is named, and that nothing at the same level already uses the key.
     *
     * <p>The same key at two different levels is allowed on purpose: that is the collision the
     * narrowest-wins rule exists to settle, and refusing it would take away the only way to say
     * "this one is different" about a single piece.
     *
     * <p>The narrow level has to sit inside the named inventory, and that is checked here rather
     * than left to the caller. A route knows only the inventory in its path, so a kind or a piece id
     * from somebody else's station paired with an inventory of one's own would otherwise write a
     * definition onto another station's gear.
     *
     * @param inventoryId the inventory, always
     * @param artId       the kind this field describes, or {@code null}
     * @param itemId      the single piece this field describes, or {@code null}
     */
    public InventoryFieldDefinition create(
            int inventoryId,
            Integer artId,
            Integer itemId,
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
        if (artId != null && itemId != null) {
            throw new IllegalArgumentException("A field belongs to a kind or to a single piece, never to both");
        }
        requireInInventory(inventoryId, artId, itemId);
        FieldConfig effectiveConfig = config != null ? config : defaultConfig(fieldType);
        if (effectiveConfig.fieldType() != fieldType) {
            throw new IllegalArgumentException("Field config type does not match field type");
        }
        for (InventoryFieldDefinition existing : sameLevel(inventoryId, artId, itemId)) {
            if (existing.key().equals(key)) {
                throw new IllegalArgumentException("Field key already exists at this level");
            }
        }
        InventoryFieldDefinition created = repository.create(
                inventoryId, artId, itemId, key, label, fieldType, required, sortOrder, effectiveConfig);
        log.info(
                "Created field definition {} (key='{}', label='{}', type={}, required={}, artId={}, itemId={}) in inventory {}",
                created.id(),
                key,
                label,
                fieldType,
                required,
                artId,
                itemId,
                inventoryId);
        return created;
    }

    /**
     * Refuses a kind or a piece that does not sit in the named inventory.
     *
     * @param inventoryId the inventory the definition is being written into
     * @param artId       the kind the definition names, or {@code null}
     * @param itemId      the piece the definition names, or {@code null}
     */
    private void requireInInventory(int inventoryId, Integer artId, Integer itemId) {
        if (artId != null
                && artRepository
                        .findById(artId)
                        .filter(art -> art.inventoryId() == inventoryId)
                        .isEmpty()) {
            throw new IllegalArgumentException("The kind is not part of this inventory");
        }
        if (itemId != null
                && inventoryRepository
                        .findItemById(itemId)
                        .filter(item -> item.inventoryId() == inventoryId)
                        .isEmpty()) {
            throw new IllegalArgumentException("The piece is not part of this inventory");
        }
    }

    private List<InventoryFieldDefinition> sameLevel(int inventoryId, Integer artId, Integer itemId) {
        if (itemId != null) return repository.findByItem(itemId);
        if (artId != null) return repository.findByArt(artId);
        return repository.findInventoryLevel(inventoryId);
    }

    /**
     * Updates the mutable parts of a field definition: label, required flag,
     * sort order and (matching-type) config. Changing the field type or key
     * is intentionally rejected - the operator deletes and re-adds instead.
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
        if (!repository.update(id, label, required, sortOrder, effective)) {
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
