/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.List;

/**
 * A drawn table of people: the columns that survived the reader's permissions, and a row each.
 *
 * <p>What is here is what the reader may see, already. A column they may not read is absent rather
 * than empty, because a column of blanks tells a room that something was withheld about these people
 * in particular. Everything that hands this to somebody, on screen or as a file, works from this one
 * object so that the three can never disagree about what was withheld.
 *
 * @param columns the columns that survived, in order
 * @param rows    one per person, values in the same order as the columns
 */
public record MemberTable(List<MemberTableHeader> columns, List<MemberTableRow> rows) {

    /**
     * A column as the reader sees it: what it is called, what it points at so a screen can tell two
     * identically named questions apart, and what its cells hold.
     */
    public record MemberTableHeader(
            String label, MemberTableColumnKind kind, String key, Integer fieldId, MemberTableCellType type) {}

    /** One person's row, in the same order as the columns. */
    public record MemberTableRow(int memberId, List<String> values) {}
}
