/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A task put to a member to answer for the gear the station has recorded against their name.
 *
 * @param stationId   the station that handed it out
 * @param memberId    the member whose gear it is about
 * @param handedOutBy who handed it out, or {@code null} once they are no longer a member here
 * @param handedOutAt when it was handed out
 * @param dueOn       the day the answer is wanted by, or {@code null} where none was named
 * @param state       where the task stands
 * @param submittedAt when it was handed in, or {@code null} while it is still open
 * @param submittedBy who entered the submission, which is the member or one of their guardians
 * @param closedAt    when it stopped needing anything, however it ended
 * @param checkId     the check the settled task wrote, once it wrote one
 */
public record SelfCheck(
        int id,
        int stationId,
        int memberId,
        Integer handedOutBy,
        Instant handedOutAt,
        LocalDate dueOn,
        SelfCheckState state,
        Instant submittedAt,
        Integer submittedBy,
        Instant closedAt,
        Integer checkId) {

    /**
     * The columns every read of this table selects, in the order the mapping expects them.
     */
    public static final String COLUMNS =
            "id, station_id, member_id, handed_out_by, handed_out_at, due_on, state, submitted_at, submitted_by, closed_at, check_id";

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<SelfCheck> map() {
        return row -> new SelfCheck(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("member_id"),
                row.getObject("handed_out_by", Integer.class),
                row.get("handed_out_at", INSTANT_TIMESTAMP),
                row.getObject("due_on", LocalDate.class),
                row.getEnum("state", SelfCheckState.class),
                row.get("submitted_at", INSTANT_TIMESTAMP),
                row.getObject("submitted_by", Integer.class),
                row.get("closed_at", INSTANT_TIMESTAMP),
                row.getObject("check_id", Integer.class));
    }

    /**
     * Whether the member may still write to this task.
     */
    public boolean open() {
        return state == SelfCheckState.OPEN;
    }
}
