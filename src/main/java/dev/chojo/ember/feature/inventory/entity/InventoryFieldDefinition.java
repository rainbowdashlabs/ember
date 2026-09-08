/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.Question;
import dev.chojo.ember.feature.question.QuestionSettings;

import java.math.BigDecimal;
import java.util.List;

/**
 * Custom field schema entry. Items carry one JSONB sub-object keyed by {@code key} inside
 * {@code inventory_item.metadata.fields}.
 *
 * <p>A definition sits at exactly one of three levels: the whole inventory, one kind of thing, or
 * one single piece. The narrow levels are what make the mechanism usable at all: a frequency band
 * is nonsense on a charging station sharing a drawer with six radios, and a plate number belongs to
 * one vehicle and nothing else.
 *
 * <p>Definitions move down a level; values never do. Six radios share the field, not the value, so
 * nothing is inherited and no piece can pick up another piece's inspection date.
 *
 * @param id          the unique field-definition identifier
 * @param inventoryId the inventory this field belongs to, always set
 * @param artId       the kind this field is defined for, or {@code null} when it is not
 * @param itemId      the single piece this field is defined for, or {@code null} when it is not
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
        Integer artId,
        Integer itemId,
        String key,
        String label,
        FieldType fieldType,
        boolean required,
        int sortOrder,
        FieldConfig config) {

    /**
     * This field as everything that checks a question reads it.
     *
     * <p>What it holds is typed already, which is why a date here has always been a date. What
     * nothing measured is whether a choice is one of the ones written down and whether a measurement
     * sits between its bounds.
     */
    public Question question() {
        return settings().withRequired(required).asQuestion(label, fieldType.kind());
    }

    /** What this field says about the question it asks, out of the settings typed per kind. */
    private QuestionSettings settings() {
        if (config instanceof FieldConfig.EnumConfig(List<FieldConfig.EnumConfig.EnumOption> options)) {
            return QuestionSettings.none()
                    .withOptions(options.stream()
                            .map(FieldConfig.EnumConfig.EnumOption::value)
                            .toList());
        }
        if (config instanceof FieldConfig.NumberConfig(BigDecimal min, BigDecimal max, BigDecimal step, String unit)) {
            return QuestionSettings.none().withBounds(min, max);
        }
        return QuestionSettings.none();
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryFieldDefinition> map() {
        return row -> {
            FieldType type = row.getEnum("field_type", FieldType.class);
            return new InventoryFieldDefinition(
                    row.getInt("id"),
                    row.getInt("inventory_id"),
                    row.getObject("art_id", Integer.class),
                    row.getObject("item_id", Integer.class),
                    row.getString("key"),
                    row.getString("label"),
                    type,
                    row.getBoolean("required"),
                    row.getInt("sort_order"),
                    FieldConfig.parse(type, row.getString("config")));
        };
    }

    /**
     * Which level this definition sits at.
     *
     * @return the level, never {@code null}
     */
    public FieldLevel level() {
        if (itemId != null) return FieldLevel.ITEM;
        if (artId != null) return FieldLevel.ART;
        return FieldLevel.INVENTORY;
    }
}
