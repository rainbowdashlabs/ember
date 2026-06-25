/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Supported custom-field types for {@link InventoryFieldDefinition}.
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
     * Decimal number with optional min/max/step/unit.
     */
    NUMBER,
    /**
     * Yes/no toggle with configurable true/false labels.
     */
    BOOLEAN
}
