/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import dev.chojo.ember.feature.question.FieldType;

/**
 * The type of a board field as it is sent to partner stations a board is shared with. The station
 * itself speaks of the shared {@link FieldType}.
 *
 * <p>The names are part of what a shared board's partners read, so they keep the spelling those
 * partners know. Nothing but the wire reads them.
 */
public enum BoardFieldType {
    STRING,
    NUMBER,
    BOOLEAN,
    ENUM,
    DATE,
    LANE_ASSIGNEE;

    /** The shared type this name stands for. */
    public FieldType fieldType() {
        return switch (this) {
            case STRING -> FieldType.TEXT;
            case ENUM -> FieldType.CHOICE;
            default -> FieldType.valueOf(name());
        };
    }

    /**
     * The wire spelling of a shared field type.
     *
     * @param type a type a board offers
     * @throws IllegalArgumentException where a board does not offer the type
     */
    public static BoardFieldType of(FieldType type) {
        return type.spelledAs(BoardFieldType.class, BoardFieldType::fieldType);
    }
}
