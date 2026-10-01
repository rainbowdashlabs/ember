/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * The custom-field types of {@link InventoryFieldDefinition} as the station's screens spell them.
 *
 * <p>Only the spelling is this feature's own: what a type means is the shared field type each of
 * these names through {@link #fieldType()}.
 */
public enum FieldType {
    /**
     * Calendar date.
     */
    DATE,
    /**
     * One value chosen from a fixed list of options carried in the field config.
     */
    ENUM,
    /**
     * Free-form text, optionally multi-line.
     */
    TEXT,
    /**
     * Number with optional min/max/step/unit, whole unless its step is below one.
     */
    NUMBER,
    /**
     * Yes/no toggle with configurable true/false labels.
     */
    BOOLEAN;

    /** The shared name this type is stored under, which is also what every check reads. */
    public dev.chojo.ember.feature.question.FieldType fieldType() {
        return this == ENUM
                ? dev.chojo.ember.feature.question.FieldType.CHOICE
                : dev.chojo.ember.feature.question.FieldType.valueOf(name());
    }

    /**
     * The type a column holds under its shared name.
     *
     * @param stored the name as the column holds it
     */
    public static FieldType stored(String stored) {
        return dev.chojo.ember.feature.question.FieldType.featureType(FieldType.class, stored, FieldType::fieldType);
    }
}
