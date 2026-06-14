/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

/**
 * The kind of change recorded in a {@link BoardTicketHistory} entry.
 */
public enum BoardTicketHistoryAction {
    TITLE_CHANGED,
    DESCRIPTION_CHANGED,
    PRIORITY_CHANGED,
    DUE_DATE_CHANGED,
    ASSIGNEE_CHANGED,
    LABEL_ADDED,
    LABEL_REMOVED,
    LINK_ADDED,
    LINK_REMOVED,
    FIELD_CHANGED
}
