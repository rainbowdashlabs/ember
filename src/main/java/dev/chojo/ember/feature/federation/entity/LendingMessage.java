/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A chat message in a lending request thread (text or system status message). The sender's
 * station is identified by its stable UUID so the message stays attributable after the sender
 * station has moved to a different Ember instance.
 */
public record LendingMessage(
        int id,
        int requestId,
        UUID senderStationUid,
        Integer senderMemberId,
        String message,
        boolean isSystem,
        Instant createdAt) {

    public static RowMapping<LendingMessage> map() {
        return row -> new LendingMessage(
                row.getInt("id"),
                row.getInt("request_id"),
                row.get("sender_station_uid", StandardValueConverter.UUID_STRING),
                row.getObject("sender_member_id", Integer.class),
                row.getString("message"),
                row.getBoolean("is_system"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
