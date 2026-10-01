/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import dev.chojo.ember.feature.question.FieldType;

/**
 * The type of a board field as it is sent: to partner stations a board is shared with, and to the
 * board screens. The station itself speaks of the shared {@link FieldType}.
 *
 * <p>The names are part of what a shared board's partners read, so they keep the spelling those
 * partners know. Each one also says which record a field's settings and a ticket's value of it are.
 */
public enum BoardFieldType {
    STRING(BoardFieldValue.StringValue.class, BoardFieldConfig.Simple.class),
    NUMBER(BoardFieldValue.NumberValue.class, BoardFieldConfig.Simple.class),
    BOOLEAN(BoardFieldValue.BooleanValue.class, BoardFieldConfig.Simple.class),
    ENUM(BoardFieldValue.EnumValue.class, BoardFieldConfig.Enum.class),
    DATE(BoardFieldValue.DateValue.class, BoardFieldConfig.Simple.class),
    LANE_ASSIGNEE(BoardFieldValue.LaneAssigneeValue.class, BoardFieldConfig.LaneAssignee.class);

    private final Class<? extends BoardFieldValue> valueClass;
    private final Class<? extends BoardFieldConfig> configClass;

    BoardFieldType(Class<? extends BoardFieldValue> valueClass, Class<? extends BoardFieldConfig> configClass) {
        this.valueClass = valueClass;
        this.configClass = configClass;
    }

    public Class<? extends BoardFieldValue> valueClass() {
        return valueClass;
    }

    public Class<? extends BoardFieldConfig> configClass() {
        return configClass;
    }

    /** The shared name this type is stored under. */
    public FieldType fieldType() {
        return switch (this) {
            case STRING -> FieldType.TEXT;
            case ENUM -> FieldType.CHOICE;
            default -> FieldType.valueOf(name());
        };
    }

    /**
     * The type a column holds under its shared name.
     *
     * @param stored the name as the column holds it
     */
    public static BoardFieldType stored(String stored) {
        return FieldType.featureType(BoardFieldType.class, stored, BoardFieldType::fieldType);
    }

    /**
     * The wire spelling of a shared field type.
     *
     * @param type a type a board offers
     * @throws IllegalArgumentException where a board does not offer the type
     */
    public static BoardFieldType of(FieldType type) {
        return stored(type.name());
    }
}
