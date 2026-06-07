/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

public enum BoardFieldType {
    STRING(BoardFieldValue.StringValue.class, BoardFieldConfig.Simple.class),
    NUMBER(BoardFieldValue.NumberValue.class, BoardFieldConfig.Simple.class),
    BOOLEAN(BoardFieldValue.BooleanValue.class, BoardFieldConfig.Simple.class),
    ENUM(BoardFieldValue.EnumValue.class, BoardFieldConfig.Enum.class),
    DATE(BoardFieldValue.DateValue.class, BoardFieldConfig.Simple.class),
    LANE_ASSIGNEE(BoardFieldValue.LaneAssignee.class, BoardFieldConfig.LaneAssignee.class);

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
}
