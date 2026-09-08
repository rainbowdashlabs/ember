/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import dev.chojo.ember.feature.question.QuestionKind;

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
    BOOLEAN;

    /**
     * The shared kind this type is, which is what the one check measures an answer against.
     *
     * <p>Gear is measured rather than counted, so a number here carries a fraction: a length in
     * metres and a weight in kilos are what these fields hold.
     */
    public QuestionKind kind() {
        return switch (this) {
            case DATE -> QuestionKind.DATE;
            case ENUM -> QuestionKind.CHOICE;
            case TEXT -> QuestionKind.TEXT;
            case NUMBER -> QuestionKind.DECIMAL;
            case BOOLEAN -> QuestionKind.BOOLEAN;
        };
    }
}
