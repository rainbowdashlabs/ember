/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What came back to the invitation an entry currently holds, or {@code null} while nothing has.
 *
 * <p>It belongs to that one invitation and goes with it: a station offering a different date
 * replaces the invitation, and an answer to the one it replaced would say nothing about the new
 * evening.
 *
 * @param answer     which of the three was clicked
 * @param answeredAt when it was clicked, so a station can see how long an invitation has been sitting
 * @param note       what they wrote alongside it, empty when they wrote nothing
 */
public record WaitingListInvitationAnswer(WaitingListAnswer answer, Instant answeredAt, String note) {

    /** Reads the answer off an entry row, or {@code null} when the invitation is unanswered. */
    public static WaitingListInvitationAnswer from(Row row) throws SQLException {
        var answer = row.getString("invitation_answer");
        if (answer == null) return null;
        return new WaitingListInvitationAnswer(
                WaitingListAnswer.valueOf(answer),
                row.get("invitation_answered_at", INSTANT_TIMESTAMP),
                row.getString("invitation_answer_note"));
    }
}
