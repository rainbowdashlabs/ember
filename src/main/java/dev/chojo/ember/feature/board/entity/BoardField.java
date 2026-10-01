/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

/**
 * A custom field of a board as it is sent: to partner stations a board is shared with, and to the
 * board screens. Every other path speaks of a {@link BoardFieldDefinition}.
 *
 * <p>Its type keeps the spelling partners already read, so a partner on an older version still
 * understands the fields of a shared board.
 *
 * @param id        the field
 * @param boardId   the board it belongs to
 * @param name      what the field is called
 * @param fieldType what kind of value it holds, as the wire spells it
 * @param config    its settings, the record its type names
 * @param position  where it stands among the board's fields
 */
public record BoardField(
        int id, int boardId, String name, BoardFieldType fieldType, BoardFieldConfig config, int position) {

    /**
     * The wire form of a field the board keeps.
     *
     * @param field the field as the station keeps it
     * @return the same field as it is sent
     */
    public static BoardField of(BoardFieldDefinition field) {
        return new BoardField(
                field.id(), field.boardId(), field.name(), field.wireType(), field.config(), field.position());
    }
}
