/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * An open name request as a station sees it: which of its members asked, the name they carry now
 * and the one they asked for.
 *
 * @param requestId     the request
 * @param memberId      the member at this station whose account asked
 * @param memberUid     the same member's uid
 * @param currentName   the register name the account carries now
 * @param requestedName the register name asked for
 * @param requestedAt   when it was asked for
 */
public record PendingNameChange(
        int requestId, int memberId, UUID memberUid, String currentName, String requestedName, Instant requestedAt) {

    public static RowMapping<PendingNameChange> map() {
        return row -> new PendingNameChange(
                row.getInt("request_id"),
                row.getInt("member_id"),
                row.get("member_uid", UUID_STRING),
                row.getString("current_name"),
                row.getString("requested_name"),
                row.get("requested_at", INSTANT_TIMESTAMP));
    }
}
