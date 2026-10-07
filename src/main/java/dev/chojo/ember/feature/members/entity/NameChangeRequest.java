/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A register name a member asked for, which only reaches the account once a member manager
 * approves it.
 *
 * @param id          the request
 * @param accountId   the account whose name is to change
 * @param firstName   the first name asked for
 * @param lastName    the last name asked for
 * @param requestedAt when it was asked for, or last asked for again
 * @param decidedBy   the account that decided, null while open and for a withdrawal
 * @param decidedAt   when it was decided, null while open
 * @param outcome     how it ended, null while open
 * @param reason      why it was denied, where somebody said
 */
public record NameChangeRequest(
        int id,
        int accountId,
        String firstName,
        String lastName,
        Instant requestedAt,
        @Nullable Integer decidedBy,
        @Nullable Instant decidedAt,
        @Nullable NameChangeOutcome outcome,
        @Nullable String reason) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS =
            "id, account_id, first_name, last_name, requested_at, decided_by, decided_at, outcome, reason";

    public static RowMapping<NameChangeRequest> map() {
        return row -> new NameChangeRequest(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.get("requested_at", INSTANT_TIMESTAMP),
                row.getObject("decided_by", Integer.class),
                row.get("decided_at", INSTANT_TIMESTAMP),
                row.getString("outcome") == null ? null : row.getEnum("outcome", NameChangeOutcome.class),
                row.getString("reason"));
    }

    /** The name asked for, written the way the account writes its full name. */
    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
