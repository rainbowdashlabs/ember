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
 * <p>Neither waits for anybody, so neither is a row of the submission. This is only the link that
 * lets a reviewer see it happened during this task.
 *
 * @param taskId     the task it was raised during
 * @param kind       whether it was a loss or an exchange
 * @param itemId     the piece it was about, or {@code null} once that piece has been deleted
 * @param movementId the movement an exchange started, where there is one
 * @param raisedBy   who raised it, which is the member or one of their guardians
 * @param raisedAt   when it was raised
 */
public record SelfCheckRaised(
        int id,
        int taskId,
        SelfCheckRaisedKind kind,
        Integer itemId,
        Integer movementId,
        Integer raisedBy,
        Instant raisedAt) {

    /**
     * The columns every read of this table selects, in the order the mapping expects them.
     */
    public static final String COLUMNS = "id, task_id, kind, item_id, movement_id, raised_by, raised_at";

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<SelfCheckRaised> map() {
        return row -> new SelfCheckRaised(
                row.getInt("id"),
                row.getInt("task_id"),
                row.getEnum("kind", SelfCheckRaisedKind.class),
                row.getObject("item_id", Integer.class),
                row.getObject("movement_id", Integer.class),
                row.getObject("raised_by", Integer.class),
                row.get("raised_at", INSTANT_TIMESTAMP));
    }
}
