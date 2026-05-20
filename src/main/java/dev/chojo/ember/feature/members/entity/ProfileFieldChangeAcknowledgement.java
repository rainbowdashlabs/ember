/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a manager's acknowledgement of a profile field change.
 *
 * @param id                 the acknowledgement identifier
 * @param changeId           the profile field change being acknowledged
 * @param acknowledgedBy     the member who acknowledged the change
 * @param acknowledgedAt     the timestamp of the acknowledgement
 * @param comment            an optional comment provided during acknowledgement
 * @param acknowledgedByName the display name of the acknowledging member
 */
public record ProfileFieldChangeAcknowledgement(
        int id, int changeId, int acknowledgedBy, Instant acknowledgedAt, String comment, String acknowledgedByName) {
    /** Creates a row mapping for database result set conversion. */
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
