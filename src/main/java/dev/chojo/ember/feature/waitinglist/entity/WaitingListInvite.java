/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record WaitingListInvite(
        int id, int listId, String code, int maxUses, int uses, Instant expiresAt, Instant createdAt) {

    public static RowMapping<WaitingListInvite> map() {
        return row -> new WaitingListInvite(
                row.getInt("id"),
                row.getInt("list_id"),
                row.getString("code"),
                row.getInt("max_uses"),
                row.getInt("uses"),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    public boolean hasUsesLeft() {
        return maxUses == -1 || uses < maxUses;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
