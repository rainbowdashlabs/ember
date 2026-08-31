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
 * One thing a member said, and what a reviewer made of it.
 *
 * <p>The row hangs on the piece where there is one and on the inventory plus a slot where the place
 * is empty, because the list itself is recomputed on every read and a position in it points at
 * something else the moment a group changes underneath it.
 *
 * @param taskId          the task this answer belongs to
 * @param itemId          the piece it is about, or {@code null} on an empty place and again once
 *                        the piece it named has been deleted
 * @param inventoryId     the inventory the piece sits in, or the one the empty place belongs to
 * @param slot            which empty place in that inventory, counted from zero, or {@code null} on
 *                        an answer about a piece
 * @param answer          what the member said
 * @param note            what they wrote beside it
 * @param typedInternalId the number they read off a piece nobody wrote down, or {@code null}
 * @param answeredBy      who entered it, which is the member or one of their guardians
 * @param answeredAt      when it was last written
 * @param state           whether a reviewer has settled it
 * @param reviewerReason  why it was refused, empty on every other row
 * @param reviewedBy      who settled it, or {@code null} while it is outstanding
 * @param reviewedAt      when it was settled, or {@code null} while it is outstanding
 */
public record SelfCheckRow(
        int id,
        int taskId,
        Integer itemId,
        int inventoryId,
        Integer slot,
        SelfCheckAnswer answer,
        String note,
        String typedInternalId,
        Integer answeredBy,
        Instant answeredAt,
        SelfCheckRowState state,
        String reviewerReason,
        Integer reviewedBy,
        Instant reviewedAt) {

    /**
     * The columns every read of this table selects, in the order the mapping expects them.
     */
    public static final String COLUMNS = "id, task_id, item_id, inventory_id, slot, answer, note, typed_internal_id,"
            + " answered_by, answered_at, state, reviewer_reason, reviewed_by, reviewed_at";

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<SelfCheckRow> map() {
        return row -> new SelfCheckRow(
                row.getInt("id"),
                row.getInt("task_id"),
                row.getObject("item_id", Integer.class),
                row.getInt("inventory_id"),
                row.getObject("slot", Integer.class),
                row.getEnum("answer", SelfCheckAnswer.class),
                row.getString("note"),
                row.getString("typed_internal_id"),
                row.getObject("answered_by", Integer.class),
                row.get("answered_at", INSTANT_TIMESTAMP),
                row.getEnum("state", SelfCheckRowState.class),
                row.getString("reviewer_reason"),
                row.getObject("reviewed_by", Integer.class),
                row.get("reviewed_at", INSTANT_TIMESTAMP));
    }

    /**
     * Whether the thing this row hangs on is gone, which happens when the piece it named has been
     * deleted since the answer was given. Such a row is shown to the reviewer as having lost its
     * anchor rather than quietly dropped.
     */
    public boolean anchorGone() {
        return itemId == null && slot == null;
    }
}
