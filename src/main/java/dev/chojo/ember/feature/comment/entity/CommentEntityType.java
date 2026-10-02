/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

/**
 * Types of entities that comments can be attached to, each with the column of the comment table
 * that names its target.
 */
public enum CommentEntityType {
    EVENT("event_id"),
    NEWS("news_id"),
    BOARD_TICKET("board_ticket_id"),
    KB("kb_file_id");

    private final String column;

    CommentEntityType(String column) {
        this.column = column;
    }

    /**
     * The column of the comment table that holds the target of this type, set on exactly the
     * comments of this type.
     *
     * @return the column name
     */
    public String column() {
        return column;
    }
}
