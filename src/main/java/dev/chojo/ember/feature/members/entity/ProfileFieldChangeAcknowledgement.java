/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record ProfileFieldChangeAcknowledgement(
        int id, int changeId, int acknowledgedBy, Instant acknowledgedAt, String comment, String acknowledgedByName) {
    public static RowMapping<ProfileFieldChangeAcknowledgement> map() {
        return row -> new ProfileFieldChangeAcknowledgement(
                row.getInt("id"),
                row.getInt("change_id"),
                row.getInt("acknowledged_by"),
                row.get("acknowledged_at", INSTANT_TIMESTAMP),
                row.getString("comment"),
                row.getString("acknowledged_by_name"));
    }
}
