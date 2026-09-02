/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A loss or an exchange a member set going while answering a self-check.
 *
 * <p>Neither is a row of the submission and neither ordinarily waits for anybody, so this is mostly
 * the link that lets a reviewer see it happened during this task.
 *
 * <p>One case waits, and the reason is not the size: it is that the report would run against a
 * record that is about to be replaced. Where the member has said the record has the wrong size
 * against a piece, putting that right does not edit the piece, it writes a new one and takes the old
 * one off their name. A loss or an exchange raised before that lands on the piece that is leaving,
 * with the size the member has just disowned. Such a report waits on the answer instead and goes out
 * when a reviewer takes the correction, which is also why this row then carries the words and the
 * wanted size: until it goes out there is no piece and no movement holding them.
 *
 * @param taskId        the task it was raised during
 * @param kind          whether it was a loss or an exchange
 * @param state         whether it has gone out, is still waiting, or never will
 * @param itemId        the piece it was about, which becomes the piece a correction produced once a
 *                      waiting report goes out, or {@code null} once that piece has been deleted
 * @param movementId    the movement an exchange started, where there is one
 * @param waitsForRowId the answer a waiting report hangs on, or {@code null} where it went out at once
 * @param newSizeId     the size a waiting exchange asks for, or {@code null}
 * @param words         what the member wrote when they raised it, empty on a report that went out at
 *                      once because it carries its own words where it landed
 * @param raisedBy      who raised it, which is the member or one of their guardians
 * @param raisedAt      when the member raised it, which on a waiting report is before it went out
 */
public record SelfCheckRaised(
        int id,
        int taskId,
        SelfCheckRaisedKind kind,
        SelfCheckRaisedState state,
        Integer itemId,
        Integer movementId,
        Integer waitsForRowId,
        Integer newSizeId,
        String words,
        Integer raisedBy,
        Instant raisedAt) {

    /**
     * The columns every read of this table selects, in the order the mapping expects them.
     */
    public static final String COLUMNS = "id, task_id, kind, state, item_id, movement_id, waits_for_row_id,"
            + " new_size_id, words, raised_by, raised_at";

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<SelfCheckRaised> map() {
        return row -> new SelfCheckRaised(
                row.getInt("id"),
                row.getInt("task_id"),
                row.getEnum("kind", SelfCheckRaisedKind.class),
                row.getEnum("state", SelfCheckRaisedState.class),
                row.getObject("item_id", Integer.class),
                row.getObject("movement_id", Integer.class),
                row.getObject("waits_for_row_id", Integer.class),
                row.getObject("new_size_id", Integer.class),
                row.getString("words"),
                row.getObject("raised_by", Integer.class),
                row.get("raised_at", INSTANT_TIMESTAMP));
    }

    /**
     * Whether this report is still held back by an answer nobody has settled.
     */
    public boolean waiting() {
        return state == SelfCheckRaisedState.WAITING;
    }
}
