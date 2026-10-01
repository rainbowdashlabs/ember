/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;

/**
 * A custom field of a board, as the board's own station keeps and checks it and the board screens
 * read it.
 *
 * <p>Its type is the shared field type. What partner stations are sent is the {@link BoardField},
 * which spells the type the way they read it.
 *
 * @param id        the field
 * @param boardId   the board it belongs to
 * @param name      what the field is called
 * @param fieldType what kind of value it holds
 * @param config    its settings, the record its type names
 * @param position  where it stands among the board's fields
 */
public record BoardFieldDefinition(
        int id, int boardId, String name, FieldType fieldType, BoardFieldConfig config, int position) {

    /**
     * This field as the one check reads it, which is what a ticket's value is measured against: a
     * date has to be a date, a choice one of the options written down and a number a whole one.
     */
    public Question question() {
        return config.settings()
                .asQuestion(name, fieldType)
                .orElseThrow(() -> new IllegalStateException("Every board field holds a value: " + fieldType));
    }

    /** The type as the wire spells it, which also says which records the settings and values are. */
    public BoardFieldType wireType() {
        return BoardFieldType.of(fieldType);
    }

    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<BoardFieldDefinition> map() {
        return row -> {
            var type = FieldType.valueOf(row.getString("field_type"));
            return new BoardFieldDefinition(
                    row.getInt("id"),
                    row.getInt("board_id"),
                    row.getString("name"),
                    type,
                    BoardFieldConfig.parse(BoardFieldType.of(type), row.getString("config")),
                    row.getInt("position"));
        };
    }
}
